package divergentdesign;


import cost.CostModel;
import columnchange.StimutaleAnneal;
import constant.Constant;
import datamodel.DataTable;
import query.Query;
import replica.MultiReplicas;
import replica.Replica;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class DivgDesign {

  private int replicaNum = Constant.REPLICA_NUMBER;
  private int loadBalanceFactor = Constant.LOAD_BALANCE_FACTOR;
  private int maxIteration = Constant.MAX_ITERATION;
  private double epsilone = Constant.EPSILONE;


  private Query[] workload;
  private DataTable data;
  private List<Query>[] workloadSubsets;

  private double totalCost;

  private Random random = SecureRandom.getInstanceStrong();

  /**
   * Constructor
   *
   * @param data
   * @param queries
   * @param replicaNum
   * @param loadBalanceFactor
   * @param maxIteration
   * @param epsilone
   * @throws NoSuchAlgorithmException
   */
  public DivgDesign(DataTable data, Query[] queries,
                    int replicaNum, int loadBalanceFactor, int maxIteration, double epsilone) throws NoSuchAlgorithmException {
    this.data = new DataTable(data);
    this.replicaNum = replicaNum;
    this.loadBalanceFactor = loadBalanceFactor;
    this.maxIteration = maxIteration;
    this.epsilone = epsilone;
    this.workload = new Query[queries.length];
    System.arraycopy(queries, 0, workload, 0, workload.length);
    workloadSubsets = new List[replicaNum];
  }

  /**
   * Constructor
   *
   * @param dataTable
   * @param queries
   * @throws NoSuchAlgorithmException
   */
  public DivgDesign(DataTable dataTable, Query[] queries) throws NoSuchAlgorithmException {
    this.data = new DataTable(dataTable);
    this.workload = new Query[queries.length];
    System.arraycopy(queries, 0, workload, 0, workload.length);
    workloadSubsets = new List[replicaNum];
  }


  /**
   * get optimal multi replicas
   *
   * @return
   */
  public MultiReplicas optimal() {
    // pick a random m-balanced design
    initDesign();
    Replica[] multiReplicas = new Replica[replicaNum];

    // prepare for the iteration
    int it = 0;
    double curCost;
    // begin iteration
    while (true) {
      // 2. check replicas generated before
      // get n configurations, and current total  cost
      for (int i = 0; i < replicaNum; i++)
        multiReplicas[i] = (Replica) recommandReplica(workloadSubsets[i]).optimal().getReplicas().keySet().toArray()[0];
      curCost = totalCost(multiReplicas);

      if (isIterationTerminate(it, curCost)) break;
      // update
      totalCost = curCost;
      it++; // this is the point when an iteration ends

      // 1. this is for next iteration
      // init sub workload sets for this iteration
      List<Query>[] curSubQueries = new List[replicaNum];
      // for each queries in workload
      for (int i = 0; i < workload.length; i++) {
        int[] order = getLeastCostConfOrder(multiReplicas, workload[i]);
        // add q to sub workload sets
        for (int j = 0; j < loadBalanceFactor; j++)
          curSubQueries[order[j]].add(new Query(workload[i]).setWeight(workload[i].getWeight() / loadBalanceFactor));
      }
      System.arraycopy(curSubQueries, 0, workloadSubsets, 0, replicaNum);
    }

    totalCost = curCost;
    MultiReplicas res = new MultiReplicas();
    for (int i = 0; i < multiReplicas.length; i++) res.add(multiReplicas[i]);
    return res;
  }


  private void initDesign() {
    for (Query q : workload)
      for (int j = 0; j < loadBalanceFactor; j++)
        workloadSubsets[this.random.nextInt(workloadSubsets.length)]
                .add(new Query(q).setWeight(q.getWeight() / loadBalanceFactor));
  }

  private StimutaleAnneal recommandReplica(List<Query> queries) {
    return new StimutaleAnneal(data, queries.toArray(new Query[0]), 1);
  }

  private boolean isIterationTerminate(int curIteration, double curCost) {
    if (totalCost == 0 || curIteration == 0) return false;
    if (Math.abs(curCost - totalCost) < epsilone) return true;
    return curIteration >= maxIteration;
  }

  private int[] getLeastCostConfOrder(Replica[] replicas, Query query) {
    Integer[] order = new Integer[replicaNum];
    for (int i = 0; i < order.length; i++) order[i] = i;
    BigDecimal[] costs = new BigDecimal[replicaNum];
    for (int i = 0; i < costs.length; i++) costs[i] = CostModel.cost(replicas[i], query);
    Arrays.sort(order, Comparator.comparing(o -> costs[o]));
    int[] res = new int[loadBalanceFactor];
    for (int i = 0; i < res.length; i++) res[i] = order[i];
    return res;
  }

  private double totalCost(Replica[] mulReplicas) {
    BigDecimal ans = new BigDecimal("0");
    for (Query query : workload) {
      BigDecimal curCost = new BigDecimal("0");
      int[] order = getLeastCostConfOrder(mulReplicas, query);
      for (int i = 0; i < loadBalanceFactor; i++)
        curCost = curCost
                .add(CostModel.cost(mulReplicas[order[i]], query)
                        .multiply(BigDecimal.valueOf(query.getWeight()))
                        .divide(BigDecimal.valueOf(loadBalanceFactor)));
      ans = ans.add(curCost);
    }
    return ans.doubleValue();
  }

}
