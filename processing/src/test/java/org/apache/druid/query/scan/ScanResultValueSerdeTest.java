package org.apache.druid.query.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanResultValueSerdeTest
{
  private static final ObjectMapper jsonMapper = new DefaultObjectMapper();

  @Test
  public void testSerdeScanResultValueCompactedList() throws IOException
  {
    String segmentId = "some_segment_id";
    List<String> columns = new ArrayList<>(Arrays.asList("col1", "col2", "col3"));
    List<String> event = new ArrayList<>(Arrays.asList(
        "prop1",
        "prop2",
        "prop3"
    ));
    List<List<String>> events = new ArrayList<>(Collections.singletonList(event));
    ScanResultValue srv = new ScanResultValue(segmentId, columns, events);
    String serialized = jsonMapper.writeValueAsString(srv);
    ScanResultValue deserialized = jsonMapper.readValue(serialized, ScanResultValue.class);
    Assert.assertEquals(srv, deserialized);
  }

  @Test
  public void testSerdeScanResultValueNonCompactedList() throws IOException
  {
    String segmentId = "some_segment_id";
    List<String> columns = new ArrayList<>(Arrays.asList("col1", "col2", "col3"));
    Map<String, Object> event = new HashMap<>();
    event.put("key1", new Integer(4));
    event.put("key2", "some_string");
    event.put("key3", new Double(4.1));
    List<Map<String, Object>> events = new ArrayList<>(Collections.singletonList(event));
    ScanResultValue srv = new ScanResultValue(segmentId, columns, events);
    String serialized = jsonMapper.writeValueAsString(srv);
    ScanResultValue deserialized = jsonMapper.readValue(serialized, ScanResultValue.class);
    Assert.assertEquals(srv, deserialized);
  }
}
