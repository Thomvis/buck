/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.io.namedpipes;

import com.facebook.buck.util.types.Unit;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/** Named pipe server that creates the named pipe. Can either read or write to the named pipe. */
public interface NamedPipeServer extends Closeable {

  /**
   * Prepare to shutdown this named pipe server. Should be called before {@link Closeable#close()}.
   * The given {@link Future} is used to signal when {@link #close()} can be called. {@link
   * #close()} is safe to call upon successful completion of this future.
   */
  void prepareToClose(Future<Unit> readerFinished)
      throws IOException, ExecutionException, TimeoutException, InterruptedException;
}