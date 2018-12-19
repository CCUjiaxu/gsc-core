/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

package org.gsc.config;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

import com.typesafe.config.ConfigFactory;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Configuration {

  private static com.typesafe.config.Config config;

  public static com.typesafe.config.Config getByFileName(final String shellConfFileName, final String confFileName) {
    if (isNoneBlank(shellConfFileName)) {
      File shellConfFile = new File(shellConfFileName);
      resolveConfigFile(shellConfFileName, shellConfFile);
      return config;
    }

    if (isBlank(confFileName)) {
      throw new IllegalArgumentException("Configuration path is required!");
    } else {
      File confFile = new File(confFileName);
      resolveConfigFile(confFileName, confFile);
      return config;
    }
  }

  private static void resolveConfigFile(String fileName, File confFile) {
    if (confFile.exists()) {
      config = ConfigFactory.parseFile(confFile);
    } else if (Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName) != null) {
      config = ConfigFactory.load(fileName);
    } else {
      throw new IllegalArgumentException("Configuration path is required! No Such file " + fileName);
    }
  }
}

