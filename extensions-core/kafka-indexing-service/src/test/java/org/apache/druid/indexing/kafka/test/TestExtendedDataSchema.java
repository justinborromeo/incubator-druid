package org.apache.druid.indexing.kafka.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.apache.druid.segment.indexing.DataSchema;

public class TestExtendedDataSchema extends DataSchema
{
  private final String asdf;

  public TestExtendedDataSchema(
      DataSchema base,
      String asdf
  )
  {
    super(
        base.getDataSource(),
        base.getParserMap(),
        base.getAggregators(),
        base.getGranularitySpec(),
        base.getTransformSpec(),
        new DefaultObjectMapper()
    );
    this.asdf = asdf;
  }

  @JsonProperty
  public String getAsdf()
  {
    return asdf;
  }
}
