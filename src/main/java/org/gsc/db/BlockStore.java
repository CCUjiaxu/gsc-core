/*
 * java-gsc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-gsc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.gsc.db;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.gsc.core.wrapper.BlockWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.core.wrapper.BlockWrapper.BlockId;
import org.gsc.core.exception.BadItemException;

@Slf4j
@Component
public class BlockStore extends GSCStoreWithRevoking<BlockWrapper> {

  @Autowired
  private BlockStore(@Value("block") String dbName) {
    super(dbName);
  }

  public List<BlockWrapper> getLimitNumber(long startNumber, long limit) {
    BlockId startBlockId = new BlockId(Sha256Hash.ZERO_HASH, startNumber);
    return revokingDB.getValuesNext(startBlockId.getBytes(), limit).stream()
        .map(bytes -> {
          try {
            return new BlockWrapper(bytes);
          } catch (BadItemException e) {
            e.printStackTrace();
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public List<BlockWrapper> getBlockByLatestNum(long getNum) {

    return revokingDB.getlatestValues(getNum).stream()
        .map(bytes -> {
          try {
            return new BlockWrapper(bytes);
          } catch (BadItemException e) {
            e.printStackTrace();
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
