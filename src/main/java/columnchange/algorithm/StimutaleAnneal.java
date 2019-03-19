package columnchange.algorithm;

import columnchange.datamodel.DataTable;
import columnchange.query.Query;
import columnchange.replica.MultiReplicas;
import columnchange.replica.Replica;
import constant.Constant;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class StimutaleAnneal {

  private static double temperature;

  // the original data table
  private static DataTable data;

  // the queries
  private static Query[] queries;

  // the solution
  private static MultiReplicas multiReplicas;

  // cost
  private static BigDecimal optimalCost;


  private static List<BigDecimal> costHistory = new ArrayList<>();

  private static int iteration = 0;
  private static int optimalCnt = 0;


  public StimutaleAnneal(DataTable datatable, Query[] queries) {
    StimutaleAnneal.data = datatable;
    StimutaleAnneal.queries = queries;
  }


  public static MultiReplicas optimal() {
    // set original temperature
    initTemperature();
    // initialize original(random) solution
    multiReplicas = initSolution();
    optimalCost = CostModel.cost(multiReplicas, queries);
    costHistory.add(optimalCost);

    // check if converge
    while (!isGlobalConverge()) {
      System.out.println(">>>>>>>t: " + temperature);
      MultiReplicas curMultiReplica = new MultiReplicas(multiReplicas);
      BigDecimal curCost = optimalCost;
      while (!isLocalConverge()) {
        // generate new solution
        MultiReplicas newMultiReplica = generateNewMultiReplica(curMultiReplica);
        BigDecimal newCost = CostModel.cost(newMultiReplica, queries);
        if (isChosen(newCost, curCost)) {
          curMultiReplica = newMultiReplica;
          curCost = newCost;
          costHistory.add(curCost);
          System.out.println("iteration" + iteration + ": " + curCost.setScale(2, BigDecimal.ROUND_HALF_UP) + ", " + getOrder(curMultiReplica));
        } else {
          System.out.println("iteration" + iteration + ": " + newCost.setScale(2, BigDecimal.ROUND_HALF_UP) + ", " + getOrder(newMultiReplica) + " drop");
        }
        iteration++;
      }
      iteration = 0;
      if (curCost.compareTo(optimalCost) == -1) {
        multiReplicas = new MultiReplicas(curMultiReplica);
        optimalCost = curCost;
        optimalCnt = 0;
      } else
        optimalCnt++;
      // decrease temperature
      decreaseTemperature();
    }
    try {
      writeHistory();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return multiReplicas;
  }

  public static Replica generateNewReplica(Replica r) { // TODO private method

    int columnNum = r.getDataTable().getColNum();
    int pos0 = 0, pos1 = 0;
    while (pos0 == pos1) {
      pos0 = (int) (Math.random() * columnNum);
      pos1 = (int) (Math.random() * columnNum);
    }
    int len = (int) (Math.random() * (columnNum - pos0) + 1);

    // a random seed
    int seed = (int) (Math.random() * 100);
    int[] newOrder = null;

    // 5/100, shuffle
    if (isIn(seed, 0, 5))
      newOrder = ArrayTransform.shuffle(r.getOrder());

      // 15/100, range shuffle
    else if (isIn(seed, 5, 20))
      newOrder = ArrayTransform.shuffle(r.getOrder(), pos0, len);

      // 20/100, swap
    else if (isIn(seed, 20, 40))
      newOrder = ArrayTransform.swap(r.getOrder(), pos0, pos1);

      // 20/100, insert before
    else if (isIn(seed, 40, 60))
      newOrder = ArrayTransform.insertBefore(r.getOrder(), pos0, pos1);

      // 20/100, insert after
    else if (isIn(seed, 60, 80))
      newOrder = ArrayTransform.insertAfter(r.getOrder(), pos0, pos1);

      // 15/100, range reverse
    else if (isIn(seed, 80, 95))
      newOrder = ArrayTransform.reverse(r.getOrder(), pos0, len);

      // 5/100, reverse
    else if (isIn(seed, 95, 100))
      newOrder = ArrayTransform.reverse(r.getOrder());

    if (newOrder == null) throw new NullPointerException();

    return new Replica(r.getOriginalDataTable(), newOrder);
  }

  public static MultiReplicas generateNewMultiReplica(MultiReplicas m) {// TODO make it private
    MultiReplicas ans;
    do {
      ans = new MultiReplicas();
      for (Map.Entry<Replica, Integer> e : m.getReplicas().entrySet())
        for (int i = 0; i < e.getValue(); i++)
          ans.add(generateNewReplica(e.getKey()));
    } while (m.equals(ans));
    return ans;
  }


  private static boolean isChosen(BigDecimal newCost, BigDecimal oldCost) {
    if (newCost.compareTo(oldCost) == -1) return true;
    double threshold = Math.exp(oldCost.subtract(newCost).doubleValue() / temperature);
    if (Math.random() <= threshold) return true;
    return false;
  }

  private static void decreaseTemperature() {
    temperature *= Constant.TEMPERATURE_DECREASE_RATE;
  }

  // 收敛准则
  private static boolean isGlobalConverge() {
    return optimalCnt == 100;
  }

  // 抽样稳定准则
  private static boolean isLocalConverge() {
    return temperature == 0
            || iteration == Constant.LOCAL_ITERATION_NUM;
  }

  private static void initTemperature() {
    MultiReplicas m;
    BigDecimal max = null;
    BigDecimal min = null;

    for (int i = 0; i < 20; i++) {
      m = initSolution();
      BigDecimal curCost = CostModel.cost(m, queries);
      if (max == null || max.compareTo(curCost) == -1)
        max = curCost;
      if (min == null || min.compareTo(curCost) == 1)
        min = curCost;
    }
    temperature = min.subtract(max)
            .divide(BigDecimal.valueOf(Math.log(Constant.TEMPERATURE_INIT_SEED)), 10, BigDecimal.ROUND_HALF_UP)
            .doubleValue();
  }

  private static MultiReplicas initSolution() {
    MultiReplicas multiReplicas = new MultiReplicas();
    for (int i = 0; i < Constant.REPLICA_NUMBER; i++)
      multiReplicas.add(new Replica(data, ArrayTransform.random(data.getColNum())));
    return multiReplicas;
  }


  private static boolean isIn(int val, int lower, int upper) {
    return val >= lower && val < upper;
  }

  public static String getOrder(Replica r) {
    return Arrays.toString(r.getOrder());
  }

  public static String getOrder(MultiReplicas m) {
    String ans = "";
    for (Map.Entry<Replica, Integer> e : m.getReplicas().entrySet())
      for (int i = 0; i < e.getValue(); i++)
        ans += getOrder(e.getKey());
    return ans;
  }

  public static double getCost(){
    return optimalCost.doubleValue();
  }

  public static List<BigDecimal> getHistory(){
    return costHistory;
  }

   private static void writeHistory() throws IOException {
    File file = new File(Constant.HISTORY_STORE_PATH);
    if(file.exists()) file.delete();
    file.createNewFile();
    PrintWriter pw = new PrintWriter(file);
    for(BigDecimal c : costHistory)
      pw.println(c);
    pw.flush();
    pw.close();
  }



}
