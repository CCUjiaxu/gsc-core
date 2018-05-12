package org.gsc.db.iterator;

import java.util.Iterator;
import java.util.Map.Entry;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.exception.BadItemException;

public class BlockIterator extends AbstractIterator<BlockCapsule> {

  public BlockIterator(Iterator<Entry<byte[], byte[]>> iterator) {
    super(iterator);
  }

  @Override
  public BlockCapsule next() {
    try {
      Entry<byte[], byte[]> entry = iterator.next();
      return new BlockCapsule(entry.getValue());
    } catch (BadItemException e) {
      e.printStackTrace();
    }
    return null;
  }
}
