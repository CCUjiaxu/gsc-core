package org.gsc.db.api;

import static com.googlecode.cqengine.query.QueryFactory.applyThresholds;
import static com.googlecode.cqengine.query.QueryFactory.ascending;
import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.orderBy;
import static com.googlecode.cqengine.query.QueryFactory.queryOptions;
import static com.googlecode.cqengine.query.QueryFactory.threshold;
import static com.googlecode.cqengine.query.option.EngineThresholds.INDEX_ORDERING_SELECTIVITY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.resultset.ResultSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.gsc.config.Parameter.DatabaseConstants;
import org.gsc.db.api.index.TransactionIndex;
import org.gsc.core.exception.NonUniqueObjectException;
import org.gsc.db.api.index.Index;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.gsc.protos.Protocol.Transaction;

@Component
@Slf4j
public class StoreAPI {

  @Autowired(required = false)
  private IndexHelper indexHelper;

  /********************************************************************************
   *                            account api                                       *
   ********************************************************************************
   */

//  public Account getAccountByAddress(String address) throws NonUniqueObjectException {
//    if (StringUtils.isEmpty(address)) {
//      logger.info("address is empty");
//      return null;
//    }
//    Index.Iface<Account> index = indexHelper.getAccountIndex();
//    try (ResultSet<Account> resultSet = index
//        .retrieve(equal(AccountIndex.Account_ADDRESS, address))) {
//      if (resultSet.isEmpty()) {
//        return null;
//      }
//
//      return resultSet.uniqueResult();
//    } catch (com.googlecode.cqengine.resultset.common.NonUniqueObjectException e) {
//      throw new NonUniqueObjectException(e);
//    }
//  }

  /********************************************************************************
   *                          block api                                           *
   ********************************************************************************
   */

//  public Block getBlockByNumber(long number) throws NonUniqueObjectException {
//    Index.Iface<Block> index = indexHelper.getBlockIndex();
//    try (ResultSet<Block> resultSet = index.retrieve(equal(BlockIndex.Block_NUMBER, number))) {
//      if (resultSet.isEmpty()) {
//        return null;
//      }
//
//      return resultSet.uniqueResult();
//    } catch (com.googlecode.cqengine.resultset.common.NonUniqueObjectException e) {
//      throw new NonUniqueObjectException(e);
//    }
//  }

  /*******************************************************************************
   *                       transaction api                                       *
   *******************************************************************************
   */
  public Transaction getTransactionById(String id) throws NonUniqueObjectException {
    if (StringUtils.isEmpty(id)) {
      logger.info("id is empty");
      return null;
    }
    Index.Iface<Transaction> index = indexHelper.getTransactionIndex();
    try (ResultSet<Transaction> resultSet = index
        .retrieve(equal(TransactionIndex.Transaction_ID, id))) {
      if (resultSet.isEmpty()) {
        return null;
      }

      return resultSet.uniqueResult();
    } catch (com.googlecode.cqengine.resultset.common.NonUniqueObjectException e) {
      throw new NonUniqueObjectException(e);
    }
  }
  
  public List<Transaction> getTransactionsFromThis(String address, long offset, long limit) {
    if (StringUtils.isEmpty(address)) {
      logger.info("address is empty");
      return Lists.newArrayList();
    }
    Index.Iface<Transaction> index = indexHelper.getTransactionIndex();
    try (ResultSet<Transaction> resultSet =
        index.retrieve(
            equal(TransactionIndex.OWNERS, address),
            QueryFactory.queryOptions(
                QueryFactory.orderBy(ascending(TransactionIndex.TIMESTAMP)),
                applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0))))) {
      if (limit > DatabaseConstants.TRANSACTIONS_COUNT_LIMIT_MAX) {
        limit = DatabaseConstants.TRANSACTIONS_COUNT_LIMIT_MAX;
      }
      return ImmutableList.copyOf(Streams.stream(resultSet).skip(offset).limit(limit).iterator());
    }
  }

  public List<Transaction> getTransactionsToThis(String address, long offset, long limit) {
    if (StringUtils.isEmpty(address)) {
      logger.info("address is empty");
      return Lists.newArrayList();
    }
    Index.Iface<Transaction> index = indexHelper.getTransactionIndex();
    try (ResultSet<Transaction> resultSet =
        index.retrieve(
            equal(TransactionIndex.TOS, address),
            QueryFactory.queryOptions(
                QueryFactory.orderBy(ascending(TransactionIndex.TIMESTAMP)),
                applyThresholds(threshold(INDEX_ORDERING_SELECTIVITY, 1.0))))) {
      if (limit > DatabaseConstants.TRANSACTIONS_COUNT_LIMIT_MAX) {
        limit = DatabaseConstants.TRANSACTIONS_COUNT_LIMIT_MAX;
      }
      return ImmutableList.copyOf(Streams.stream(resultSet).skip(offset).limit(limit).iterator());
    }
  }

  /*******************************************************************************
   *                            witness api                                      *
   *******************************************************************************
   */
//  public List<Witness> getWitnessAll() {
//    Index.Iface<Witness> index = indexHelper.getWitnessIndex();
//    return ImmutableList.copyOf(index);
//  }

  /********************************************************************************
   *                         AssetIssue api                                       *
   ********************************************************************************
   */
//  public List<AssetIssueContract> getAssetIssueAll() {
//    Index.Iface<AssetIssueContract> index = indexHelper.getAssetIssueIndex();
//    return ImmutableList.copyOf(index);
//  }
}
