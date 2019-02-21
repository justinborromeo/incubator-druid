package org.apache.druid;

import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.guava.Yielder;
import org.apache.druid.java.util.common.guava.YieldingAccumulator;
import org.apache.druid.java.util.common.guava.YieldingSequenceBase;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;

public class SandboxTest
{
  Sequence<Integer> seq1;

  @Before
  public void setup() {
    seq1 = new BaseSequence(new BaseSequence.IteratorMaker()
    {
      @Override
      public Iterator make()
      {
        return Arrays.asList(1, 2, 3, 4, 5, 6).iterator();
      }

      @Override
      public void cleanup(Iterator iterFromMake)
      {
      }
    });

  }

  @Test
  public void test1() {
    YieldingSequenceBase<Integer>
  }

  private static class BatchingSequence extends YieldingSequenceBase<Integer> {
    Sequence<Integer> input;
    int batchSize;

    public BatchingSequence(Sequence<Integer> input, int batchSize)
    {
      this.input = input;
      this.batchSize = batchSize;
    }
    @Override
    public <OutType> Yielder<OutType> toYielder(
        OutType initValue, YieldingAccumulator<Sequence<Integer>, Sequence<Integer>> accumulator
    ) {
      Yielder<Sequence<Integer>> yielderYielder = input.toYielder(
          null,
          new YieldingAccumulator<Sequence<Integer>, Sequence<Integer>>()
          {
            @Override
            public Sequence<Integer> accumulate(
                Sequence<Integer> accumulated, Sequence<Integer> in
            )
            {
              yield();
              return in;
            }
          }
      );

      try {
        return makeYielder(yielderYielder, initValue, accumulator);
      }
      catch (Throwable t) {
        try {
          yielderYielder.close();
        }
        catch (Exception e) {
          t.addSuppressed(e);
        }
        throw t;
      }
    }
  }
}
