package com.lucidworks.spark;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * Base class for testing RDDProcessor implementations.
 */
public class RDDProcessorTestBase extends TestSolrCloudClusterSupport implements Serializable {

  protected transient JavaSparkContext jsc;

  @Before
  public void setupJavaSparkContext() {
    SparkConf conf = new SparkConf()
      .setMaster("local")
      .setAppName("test")
      .set("spark.default.parallelism", "1");
    jsc = new JavaSparkContext(conf);
  }

  @After
  public void stopSparkContext() {
    jsc.stop();
  }

  protected void buildCollection(String zkHost, String collection) throws Exception {
    String[] inputDocs = new String[] { collection+"-1,foo,bar,1", collection+"-2,foo,baz,2", collection+"-3,bar,baz,3" };
    buildCollection(zkHost, collection, inputDocs);
  }

  protected void buildCollection(String zkHost, String collection, String[] inputDocs) throws Exception {
    String confName = "testConfig";
    File confDir = new File("src/test/resources/conf");
    int numShards = 2;
    int replicationFactor = 1;
    createCollection(collection, numShards, replicationFactor, 2, confName, confDir);

    // index some docs into both collections
    int numDocsIndexed = indexDocs(zkHost, collection, inputDocs);
    Thread.sleep(1000L);

    // verify docs got indexed ... relies on soft auto-commits firing frequently
    SolrRDD solrRDD = new SolrRDD(zkHost, collection);
    JavaRDD<SolrDocument> resultsRDD = solrRDD.query(jsc.sc(), "*:*");
    long numFound = resultsRDD.count();
    assertTrue("expected "+numDocsIndexed+" docs in query results from "+collection+", but got "+numFound,
      numFound == (long)numDocsIndexed);
  }

  protected int indexDocs(String zkHost, String collection, String[] inputDocs) {
    JavaRDD<String> input = jsc.parallelize(Arrays.asList(inputDocs), 1);
    JavaRDD<SolrInputDocument> docs = input.map(new Function<String, SolrInputDocument>() {
      public SolrInputDocument call(String row) throws Exception {
        String[] fields = row.split(",");
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", fields[0]);
        doc.setField("field1_s", fields[1]);
        doc.setField("field2_s", fields[2]);
        doc.setField("field3_i", Integer.parseInt(fields[3]));
        return doc;
      }
    });
    SolrSupport.indexDocs(zkHost, collection, 1, docs);
    return inputDocs.length;
  }
}
