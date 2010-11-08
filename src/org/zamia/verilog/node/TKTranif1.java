/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;
import org.zamia.SourceFile;

@SuppressWarnings("nls")
public final class TKTranif1 extends Token
{
    public TKTranif1(int line, int pos, SourceFile sf)
    {
        super ("tranif1", line, pos, sf);
    }

    @Override
    public Object clone()
    {
      return new TKTranif1(getLine(), getPos(), getSource());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTKTranif1(this);
    }

    @Override
    public void setText(@SuppressWarnings("unused") String text)
    {
        throw new RuntimeException("Cannot change TKTranif1 text.");
    }
}