""" discovery and running of std-library "unittest" style tests. """
import sys
import traceback
import types
from typing import Any
from typing import Callable
from typing import Generator
from typing import Iterable
from typing import List
from typing import Optional
from typing import Tuple
from typing import Union

import _pytest._code
import pytest
from _pytest.compat import getimfunc
from _pytest.compat import is_async_function
from _pytest.compat import TYPE_CHECKING
from _pytest.config import hookimpl
from _pytest.fixtures import FixtureRequest
from _pytest.nodes import Collector
from _pytest.nodes import Item
from _pytest.outcomes import exit
from _pytest.outcomes import fail
from _pytest.outcomes import skip
from _pytest.outcomes import xfail
from _pytest.python import Class
from _pytest.python import Function
from _pytest.python import PyCollector
from _pytest.runner import CallInfo
from _pytest.skipping import skipped_by_mark_key
from _pytest.skipping import unexpectedsuccess_key

if TYPE_CHECKING:
    import unittest
    from typing import Type

    from _pytest.fixtures import _Scope

    _SysExcInfoType = Union[
        Tuple[Type[BaseException], BaseException, types.TracebackType],
        Tuple[None, None, None],
    ]


def pytest_pycollect_makeitem(
    collector: PyCollector, name: str, obj: object
) -> Optional["UnitTestCase"]:
    # has unittest been imported and is obj a subclass of its TestCase?
    try:
        ut = sys.modules["unittest"]
        # Type ignored because `ut` is an opaque module.
        if not issubclass(obj, ut.TestCase):  # type: ignore
            return None
    except Exception:
        return None
    # yes, so let's collect it
    item = UnitTestCase.from_parent(collector, name=name, obj=obj)  # type: UnitTestCase
    return item


class UnitTestCase(Class):
    # marker for fixturemanger.getfixtureinfo()
    # to declare that our children do not support funcargs
    nofuncargs = True

    def collect(self) -> Iterable[Union[Item, Collector]]:
        from unittest import TestLoader

        cls = self.obj
        if not getattr(cls, "__test__", True):
            return

        skipped = _is_skipped(cls)
        if not skipped:
            self._inject_setup_teardown_fixtures(cls)
            self._inject_setup_class_fixture()

        self.session._fixturemanager.parsefactories(self, unittest=True)
        loader = TestLoader()
        foundsomething = False
        for name in loader.getTestCaseNames(self.obj):
            x = getattr(self.obj, name)
            if not getattr(x, "__test__", True):
                continue
            funcobj = getimfunc(x)
            yield TestCaseFunction.from_parent(self, name=name, callobj=funcobj)
            foundsomething = True

        if not foundsomething:
            runtest = getattr(self.obj, "runTest", None)
            if runtest is not None:
                ut = sys.modules.get("twisted.trial.unittest", None)
                # Type ignored because `ut` is an opaque module.
                if ut is None or runtest != ut.TestCase.runTest:  # type: ignore
                    yield TestCaseFunction.from_parent(self, name="runTest")

    def _inject_setup_teardown_fixtures(self, cls: type) -> None:
        """Injects a hidden auto-use fixture to invoke setUpClass/setup_method and corresponding
        teardown functions (#517)"""
        class_fixture = _make_xunit_fixture(
            cls, "setUpClass", "tearDownClass", scope="class", pass_self=False
        )
        if class_fixture:
            cls.__pytest_class_setup = class_fixture  # type: ignore[attr-defined]

        method_fixture = _make_xunit_fixture(
            cls, "setup_method", "teardown_method", scope="function", pass_self=True
        )
        if method_fixture:
            cls.__pytest_method_setup = method_fixture  # type: ignore[attr-defined]


def _make_xunit_fixture(
    obj: type, setup_name: str, teardown_name: str, scope: "_Scope", pass_self: bool
):
    setup = getattr(obj, setup_name, None)
    teardown = getattr(obj, teardown_name, None)
    if setup is None and teardown is None:
        return None

    @pytest.fixture(scope=scope, autouse=True)
    def fixture(self, request: FixtureRequest) -> Generator[None, None, None]:
        if _is_skipped(self):
            reason = self.__unittest_skip_why__
            pytest.skip(reason)
        if setup is not None:
            if pass_self:
                setup(self, request.function)
            else:
                setup()
        yield
        if teardown is not None:
            if pass_self:
                teardown(self, request.function)
            else:
                teardown()

    return fixture


class TestCaseFunction(Function):
    nofuncargs = True
    _excinfo = None  # type: Optional[List[_pytest._code.ExceptionInfo]]
    _testcase = None  # type: Optional[unittest.TestCase]

    def setup(self) -> None:
        # a bound method to be called during teardown() if set (see 'runtest()')
        self._explicit_tearDown = None  # type: Optional[Callable[[], None]]
        assert self.parent is not None
        self._testcase = self.parent.obj(self.name)  # type: ignore[attr-defined]
        self._obj = getattr(self._testcase, self.name)
        if hasattr(self, "_request"):
            self._request._fillfixtures()

    def teardown(self) -> None:
        if self._explicit_tearDown is not None:
            self._explicit_tearDown()
            self._explicit_tearDown = None
        self._testcase = None
        self._obj = None

    def startTest(self, testcase: "unittest.TestCase") -> None:
        pass

    def _addexcinfo(self, rawexcinfo: "_SysExcInfoType") -> None:
        # unwrap potential exception info (see twisted trial support below)
        rawexcinfo = getattr(rawexcinfo, "_rawexcinfo", rawexcinfo)
        try:
            excinfo = _pytest._code.ExceptionInfo(rawexcinfo)  # type: ignore[arg-type]
            # invoke the attributes to trigger storing the traceback
            # trial causes some issue there
            excinfo.value
            excinfo.traceback
        except TypeError:
            try:
                try:
                    values = traceback.format_exception(*rawexcinfo)
                    values.insert(
                        0,
                        "NOTE: Incompatible Exception Representation, "
                        "displaying natively:\n\n",
                    )
                    fail("".join(values), pytrace=False)
                except (fail.Exception, KeyboardInterrupt):
                    raise
                except BaseException:
                    fail(
                        "ERROR: Unknown Incompatible Exception "
                        "representation:\n%r" % (rawexcinfo,),
                        pytrace=False,
                    )
            except KeyboardInterrupt:
                raise
            except fail.Exception:
                excinfo = _pytest._code.ExceptionInfo.from_current()
        self.__dict__.setdefault("_excinfo", []).append(excinfo)

    def addError(
        self, testcase: "unittest.TestCase", rawexcinfo: "_SysExcInfoType"
    ) -> None:
        try:
            if isinstance(rawexcinfo[1], exit.Exception):
                exit(rawexcinfo[1].msg)
        except TypeError:
            pass
        self._addexcinfo(rawexcinfo)

    def addFailure(
        self, testcase: "unittest.TestCase", rawexcinfo: "_SysExcInfoType"
    ) -> None:
        self._addexcinfo(rawexcinfo)

    def addSkip(self, testcase: "unittest.TestCase", reason: str) -> None:
        try:
            skip(reason)
        except skip.Exception:
            self._store[skipped_by_mark_key] = True
            self._addexcinfo(sys.exc_info())

    def addExpectedFailure(
        self,
        testcase: "unittest.TestCase",
        rawexcinfo: "_SysExcInfoType",
        reason: str = "",
    ) -> None:
        try:
            xfail(str(reason))
        except xfail.Exception:
            self._addexcinfo(sys.exc_info())

    def addUnexpectedSuccess(
        self, testcase: "unittest.TestCase", reason: str = ""
    ) -> None:
        self._store[unexpectedsuccess_key] = reason

    def addSuccess(self, testcase: "unittest.TestCase") -> None:
        pass

    def stopTest(self, testcase: "unittest.TestCase") -> None:
        pass

    def _expecting_failure(self, test_method) -> bool:
        """Return True if the given unittest method (or the entire class) is marked
        with @expectedFailure"""
        expecting_failure_method = getattr(
            test_method, "__unittest_expecting_failure__", False
        )
        expecting_failure_class = getattr(self, "__unittest_expecting_failure__", False)
        return bool(expecting_failure_class or expecting_failure_method)

    def runtest(self) -> None:
        from _pytest.debugging import maybe_wrap_pytest_function_for_tracing

        assert self._testcase is not None

        maybe_wrap_pytest_function_for_tracing(self)

        # let the unittest framework handle async functions
        if is_async_function(self.obj):
            # Type ignored because self acts as the TestResult, but is not actually one.
            self._testcase(result=self)  # type: ignore[arg-type]
        else:
            # when --pdb is given, we want to postpone calling tearDown() otherwise
            # when entering the pdb prompt, tearDown() would have probably cleaned up
            # instance variables, which makes it difficult to debug
            # arguably we could always postpone tearDown(), but this changes the moment where the
            # TestCase instance interacts with the results object, so better to only do it
            # when absolutely needed
            if self.config.getoption("usepdb") and not _is_skipped(self.obj):
                self._explicit_tearDown = self._testcase.tearDown
                setattr(self._testcase, "tearDown", lambda *args: None)

            # we need to update the actual bound method with self.obj, because
            # wrap_pytest_function_for_tracing replaces self.obj by a wrapper
            setattr(self._testcase, self.name, self.obj)
            try:
                self._testcase(result=self)  # type: ignore[arg-type]
            finally:
                delattr(self._testcase, self.name)

    def _prunetraceback(self, excinfo: _pytest._code.ExceptionInfo) -> None:
        Function._prunetraceback(self, excinfo)
        traceback = excinfo.traceback.filter(
            lambda x: not x.frame.f_globals.get("__unittest")
        )
        if traceback:
            excinfo.traceback = traceback


@hookimpl(tryfirst=True)
def pytest_runtest_makereport(item: Item, call: CallInfo[None]) -> None:
    if isinstance(item, TestCaseFunction):
        if item._excinfo:
            call.excinfo = item._excinfo.pop(0)
            try:
                del call.result
            except AttributeError:
                pass

    unittest = sys.modules.get("unittest")
    if (
        unittest
        and call.excinfo
        and isinstance(call.excinfo.value, unittest.SkipTest)  # type: ignore[attr-defined]
    ):
        excinfo = call.excinfo
        # let's substitute the excinfo with a pytest.skip one
        call2 = CallInfo[None].from_call(
            lambda: pytest.skip(str(excinfo.value)), call.when
        )
        call.excinfo = call2.excinfo


# twisted trial support


@hookimpl(hookwrapper=True)
def pytest_runtest_protocol(item: Item) -> Generator[None, None, None]:
    if isinstance(item, TestCaseFunction) and "twisted.trial.unittest" in sys.modules:
        ut = sys.modules["twisted.python.failure"]  # type: Any
        Failure__init__ = ut.Failure.__init__
        check_testcase_implements_trial_reporter()

        def excstore(
            self, exc_value=None, exc_type=None, exc_tb=None, captureVars=None
        ):
            if exc_value is None:
                self._rawexcinfo = sys.exc_info()
            else:
                if exc_type is None:
                    exc_type = type(exc_value)
                self._rawexcinfo = (exc_type, exc_value, exc_tb)
            try:
                Failure__init__(
                    self, exc_value, exc_type, exc_tb, captureVars=captureVars
                )
            except TypeError:
                Failure__init__(self, exc_value, exc_type, exc_tb)

        ut.Failure.__init__ = excstore
        yield
        ut.Failure.__init__ = Failure__init__
    else:
        yield


def check_testcase_implements_trial_reporter(done: List[int] = []) -> None:
    if done:
        return
    from zope.interface import classImplements
    from twisted.trial.itrial import IReporter

    classImplements(TestCaseFunction, IReporter)
    done.append(1)


def _is_skipped(obj) -> bool:
    """Return True if the given object has been marked with @unittest.skip"""
    return bool(getattr(obj, "__unittest_skip__", False))