package org.gsc.core.sync;

import java.util.Deque;
import org.gsc.core.chain.BlockWrapper;
import org.gsc.core.chain.TransactionWrapper;

public class PendingStatus {
  Deque<TransactionWrapper> pedingTxs;

  BlockWrapper pedingBlock;
}
