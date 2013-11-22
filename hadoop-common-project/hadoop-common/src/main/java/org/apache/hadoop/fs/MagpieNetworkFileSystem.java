/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileDescriptor;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.nativeio.NativeIO;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.Shell;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.fs.RawLocalFileSystem.RawLocalFileStatus;

public class MagpieNetworkFileSystem extends RawLocalFileSystem {
  public static final URI NAME = URI.create("magpienetworkfs:///");
  private String base_dir = null;  
  
  public MagpieNetworkFileSystem() {
    super();
  }
  
  private Path pathWithBase(Path f) {
    if (f != null && !f.isAbsolute() && base_dir != null) {
	return new Path(base_dir, f);
    }  
    return f;
  }

  @Override
  public URI getUri() { return NAME; }
  
  @Override
  public void initialize(URI uri, Configuration conf) throws IOException {
    super.initialize(uri, conf);
    setConf(conf);
    base_dir = conf.get("fs.magpienetworkfs.base.dir");
    if (base_dir != null) {
	setWorkingDirectory(new Path(base_dir));
    }
  }

  @Override
  public FSDataInputStream open(Path f, int bufferSize) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.open(ftmp, bufferSize);
  }
  
  @Override
  public FSDataOutputStream append(Path f, int bufferSize,
      Progressable progress) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.append(ftmp, bufferSize, progress);
  }

  @Override
  public FSDataOutputStream create(Path f, boolean overwrite, int bufferSize,
    short replication, long blockSize, Progressable progress)
    throws IOException {
    Path ftmp = pathWithBase(f);
    return super.create(ftmp, overwrite, bufferSize, replication, blockSize, progress);
  }

  @Override
  @Deprecated
  public FSDataOutputStream createNonRecursive(Path f, FsPermission permission,
      EnumSet<CreateFlag> flags, int bufferSize, short replication, long blockSize,
      Progressable progress) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.createNonRecursive(ftmp, permission, flags, bufferSize, replication, blockSize, progress); 
  }

  @Override
  public FSDataOutputStream create(Path f, FsPermission permission,
    boolean overwrite, int bufferSize, short replication, long blockSize,
    Progressable progress) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.create(ftmp, permission, overwrite, bufferSize, replication, blockSize, progress);
  }

  @Override
  public FSDataOutputStream createNonRecursive(Path f, FsPermission permission,
      boolean overwrite,
      int bufferSize, short replication, long blockSize,
      Progressable progress) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.createNonRecursive(ftmp, permission, overwrite, bufferSize, replication, blockSize, progress);
  }

  @Override
  public boolean rename(Path src, Path dst) throws IOException {
    Path srctmp = pathWithBase(src);
    Path dsttmp = pathWithBase(dst);
    return super.rename(srctmp, dsttmp);
  }
  
  @Override
  public boolean delete(Path p, boolean recursive) throws IOException {
    Path ptmp = pathWithBase(p);
    return super.delete(ptmp, recursive); 
  }
 
  @Override
  public FileStatus[] listStatus(Path f) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.listStatus(ftmp);
  }

  @Override
  public boolean mkdirs(Path f) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.mkdirs(ftmp); 
  }

  @Override
  public boolean mkdirs(Path f, FsPermission permission) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.mkdirs(ftmp, permission); 
  }

  @Override
  protected boolean primitiveMkdir(Path f, FsPermission absolutePermission)
    throws IOException {
    Path ftmp = pathWithBase(f);
    return super.mkdirs(ftmp, absolutePermission); 
  }

  @Override
  protected Path getInitialWorkingDirectory() {
    if (base_dir == null) {
      return this.makeQualified(new Path(System.getProperty("user.dir")));
    }
    else {
      return new Path(base_dir);
    }
  }
  
  @Override
  public long getDefaultBlockSize() {
    return getConf().getLong("fs.magpienetworkfs.block.size", 32 * 1024 * 1024);
  }

  @Override
  public long getDefaultBlockSize(Path f) {
    return getDefaultBlockSize();
  }

  @Override
  public FsStatus getStatus(Path p) throws IOException {
    Path ptmp = pathWithBase(p);
    return super.getStatus(ptmp); 
  }
  
  @Override
  public String toString() {
    return "MagpieNetworkFS";
  }
}
