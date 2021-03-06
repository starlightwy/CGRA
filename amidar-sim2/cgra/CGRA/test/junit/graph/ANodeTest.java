package junit.graph;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import graph.ANode;

import static target.UltraSynth.OP;

/**
 * Unit tests for {@code ANode}s.
 * 
 * @author Andreas Engel [engel@esa.cs.tu-darmstadt.de]
 */
public class ANodeTest {
  
  /**
   * Global test setup
   */
  @BeforeClass
  public static void init() {
    target.Processor.Instance = target.UltraSynth.Instance;
  }

  /**
   * Test constructor of constants
   */
  @Test
  public void testConstantNode() {
    ANode n = new ANode.Constant(0, "name", 1.0);
    assertThat(n.getOperation(), sameInstance(OP.CONST));
  }

  /**
   * Test constructor of unary operators
   */
  @Test
  public void testUnaryNode() {
    OP op = OP.NEG;
    ANode n = new ANode.Operation(0, "name", op);
    assertThat(n.getOperation(),           sameInstance(op));
    assertThat(n.getPredecessors().length, equalTo(1));
  }
  
  /**
   * Test constructor of binary operators
   */
  @Test
  public void testBinaryNode() {
    OP op = OP.ADD;
    ANode n = new ANode.Operation(0, "name", op);
    assertThat(n.getOperation(),           sameInstance(op));
    assertThat(n.getPredecessors().length, equalTo(2));
  }
  
}

/*
 * Copyright (c) 2016,
 * Embedded Systems and Applications Group,
 * Department of Computer Science,
 * TU Darmstadt,
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the institute nor the names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **********************************************************************************************************************/