/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.android;

import com.facebook.buck.jvm.java.CalculateAbi;
import com.facebook.buck.jvm.java.HasJavaAbi;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.java.JavaLibraryRules;
import com.facebook.buck.jvm.java.JavacFactory;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacToJarStepFactory;
import com.facebook.buck.jvm.java.PrebuiltJar;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import java.util.Optional;

/**
 * Description for a {@link BuildRule} that wraps an {@code .aar} file as an Android dependency.
 * <p>
 * This represents an Android Library Project packaged as an {@code .aar} bundle as specified by:
 * http://tools.android.com/tech-docs/new-build-system/aar-format. When it is in the packageable
 * deps of an {@link AndroidBinary}, its contents will be included in the generated APK.
 * <p>
 * Note that the {@code aar} may be specified as a {@link SourcePath}, so it could be either
 * a binary {@code .aar} file checked into version control, or a zip file that conforms to the
 * {@code .aar} specification that is generated by another build rule.
 */
public class AndroidPrebuiltAarDescription
    implements Description<AndroidPrebuiltAarDescription.Arg>, Flavored {

  private static final Flavor AAR_PREBUILT_JAR_FLAVOR = InternalFlavor.of("aar_prebuilt_jar");
  public static final Flavor AAR_UNZIP_FLAVOR = InternalFlavor.of("aar_unzip");

  private static final ImmutableSet<Flavor> KNOWN_FLAVORS =
      ImmutableSet.of(AAR_PREBUILT_JAR_FLAVOR, AAR_UNZIP_FLAVOR);

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return Sets.difference(flavors, KNOWN_FLAVORS).isEmpty();
  }

  private final JavaBuckConfig javaBuckConfig;
  private final JavacOptions javacOptions;

  public AndroidPrebuiltAarDescription(
      JavaBuckConfig javaBuckConfig,
      JavacOptions javacOptions) {
    this.javaBuckConfig = javaBuckConfig;
    this.javacOptions = javacOptions;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver buildRuleResolver,
      CellPathResolver cellRoots,
      A args) throws NoSuchBuildTargetException {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);

    ImmutableSet<Flavor> flavors = params.getBuildTarget().getFlavors();
    if (flavors.contains(AAR_UNZIP_FLAVOR)) {
      Preconditions.checkState(flavors.size() == 1);
      BuildRuleParams unzipAarParams = params.copyReplacingDeclaredAndExtraDeps(
          Suppliers.ofInstance(ImmutableSortedSet.of()),
          Suppliers.ofInstance(ImmutableSortedSet.copyOf(
              ruleFinder.filterBuildRuleInputs(args.aar))));
      return new UnzipAar(unzipAarParams, args.aar);
    }

    BuildRule unzipAarRule =
        buildRuleResolver.requireRule(params.getBuildTarget().withFlavors(AAR_UNZIP_FLAVOR));
    Preconditions.checkState(
        unzipAarRule instanceof UnzipAar,
        "aar_unzip flavor created rule of unexpected type %s for target %s",
        unzipAarRule.getClass(),
        params.getBuildTarget());
    UnzipAar unzipAar = (UnzipAar) unzipAarRule;

    if (HasJavaAbi.isClassAbiTarget(params.getBuildTarget())) {
      return CalculateAbi.of(
          params.getBuildTarget(),
          ruleFinder,
          params,
          new ExplicitBuildTargetSourcePath(
              unzipAar.getBuildTarget(),
              unzipAar.getPathToClassesJar()));
    }

    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    Iterable<PrebuiltJar> javaDeps = Iterables.concat(
        Iterables.filter(
            buildRuleResolver.getAllRules(args.deps),
            PrebuiltJar.class),
        Iterables.transform(
            Iterables.filter(
                buildRuleResolver.getAllRules(args.deps),
                AndroidPrebuiltAar.class),
            AndroidPrebuiltAar::getPrebuiltJar));

    if (flavors.contains(AAR_PREBUILT_JAR_FLAVOR)) {
      Preconditions.checkState(
          flavors.size() == 1,
          "Expected only flavor to be %s but also found %s",
          AAR_PREBUILT_JAR_FLAVOR,
          flavors);
      BuildRuleParams buildRuleParams = params
          .copyReplacingDeclaredAndExtraDeps(
              Suppliers.ofInstance(ImmutableSortedSet.copyOf(javaDeps)),
              Suppliers.ofInstance(ImmutableSortedSet.of(unzipAar)));
      return new PrebuiltJar(
        /* params */ buildRuleParams,
        /* resolver */ pathResolver,
        /* binaryJar */ new ExplicitBuildTargetSourcePath(
          unzipAar.getBuildTarget(),
          unzipAar.getPathToClassesJar()),
        /* sourceJar */ Optional.empty(),
        /* gwtJar */ Optional.empty(),
        /* javadocUrl */ Optional.empty(),
        /* mavenCoords */ Optional.empty(),
        /* provided */ false);
    }

    if (flavors.contains(AndroidResourceDescription.AAPT2_COMPILE_FLAVOR)) {
      return new Aapt2Compile(
          params.copyAppendingExtraDeps(unzipAarRule),
          unzipAar.getResDirectory());
    }

    BuildRule prebuiltJarRule = buildRuleResolver.requireRule(
        BuildTargets.createFlavoredBuildTarget(
            params.getBuildTarget().checkUnflavored(),
            AAR_PREBUILT_JAR_FLAVOR));
    Preconditions.checkState(
        prebuiltJarRule instanceof PrebuiltJar,
        "%s flavor created rule of unexpected type %s for target %s",
        AAR_PREBUILT_JAR_FLAVOR,
        unzipAarRule.getType(),
        params.getBuildTarget());
    PrebuiltJar prebuiltJar = (PrebuiltJar) prebuiltJarRule;

    Preconditions.checkArgument(
        flavors.isEmpty(),
        "Unexpected flavors for android_prebuilt_aar: %s",
        flavors);

    BuildRuleParams androidLibraryParams = params.copyReplacingDeclaredAndExtraDeps(
        /* declaredDeps */ Suppliers.ofInstance(ImmutableSortedSet.of(prebuiltJar)),
        /* extraDeps */ Suppliers.ofInstance(ImmutableSortedSet.of(unzipAar)));
    return new AndroidPrebuiltAar(
        androidLibraryParams,
        /* resolver */ pathResolver,
        ruleFinder,
        /* proguardConfig */ new ExplicitBuildTargetSourcePath(
            unzipAar.getBuildTarget(),
            unzipAar.getProguardConfig()),
        /* nativeLibsDirectory */ new ExplicitBuildTargetSourcePath(
            unzipAar.getBuildTarget(),
            unzipAar.getNativeLibsDirectory()),
        /* prebuiltJar */ prebuiltJar,
        /* unzipRule */ unzipAar,
        /* javacOptions */ javacOptions,
        new JavacToJarStepFactory(
            JavacFactory.create(ruleFinder, javaBuckConfig, null),
            javacOptions,
            new BootClasspathAppender()),
        javaBuckConfig.shouldSuggestDependencies(),
        /* exportedDeps */ javaDeps,
        JavaLibraryRules.getAbiSourcePaths(buildRuleResolver, androidLibraryParams.getBuildDeps()));
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AbstractDescriptionArg {
    public SourcePath aar;
    public Optional<SourcePath> sourceJar;
    public Optional<String> javadocUrl;
    public ImmutableSortedSet<BuildTarget> deps = ImmutableSortedSet.of();
  }

}
