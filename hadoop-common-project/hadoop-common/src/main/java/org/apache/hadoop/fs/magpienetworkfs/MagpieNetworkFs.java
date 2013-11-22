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
package org.apache.hadoop.fs.magpienetworkfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.AbstractFileSystem;
import org.apache.hadoop.fs.DelegateToFileSystem;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FsConstants;
import org.apache.hadoop.fs.FsServerDefaults;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.MagpieNetworkFileSystem;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.local.RawLocalFs;
import org.apache.hadoop.fs.local.LocalConfigKeys;
import org.apache.hadoop.util.Shell;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MagpieNetworkFs extends RawLocalFs {
  private String base_dir = null;

  private static final Log LOG = LogFactory.getLog(MagpieNetworkFs.class);

  private Path pathWithBase(Path f) {
    if (f != null && !f.isAbsolute() && base_dir != null) {
	return new Path(base_dir, f);
    }  
    return f;
  }

  MagpieNetworkFs(final Configuration conf) throws IOException, URISyntaxException {
    this(MagpieNetworkFileSystem.NAME, conf);
    base_dir = conf.get("fs.magpienetworkfs.base.dir");
  }
  
  /**
   * This constructor has the signature needed by
   * {@link AbstractFileSystem#createFileSystem(URI, Configuration)}.
   * 
   * @param theUri which must be that of localFs
   * @param conf
   * @throws IOException
   * @throws URISyntaxException 
   */
  MagpieNetworkFs(final URI theUri, final Configuration conf) throws IOException,
      URISyntaxException {
    super(theUri, new MagpieNetworkFileSystem(), conf, 
	  MagpieNetworkFileSystem.NAME.getScheme(), false);
    base_dir = conf.get("fs.magpienetworkfs.base.dir");
  }
  
  @Override
  public void createSymlink(Path target, Path link, boolean createParent) 
      throws IOException {
    final String targetScheme = target.toUri().getScheme();
    if (targetScheme != null
	&& !"file".equals(targetScheme)
	&& !"magpienetworkfs".equals(targetScheme)) {
      throw new IOException("Unable to create symlink to non-local file "+
                            "system: "+target.toString());
    }
    Path targettmp = pathWithBase(target);
    Path linktmp = pathWithBase(link);
    super.createSymlink(targettmp, linktmp, createParent);
  }

  @Override
  public FileStatus getFileLinkStatus(final Path f) throws IOException {
    Path ftmp = pathWithBase(f);
    return super.getFileLinkStatus(ftmp);
  }
}
