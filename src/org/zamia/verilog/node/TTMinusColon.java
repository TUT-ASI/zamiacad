/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;
import org.zamia.SourceFile;

@SuppressWarnings("nls")
public final class TTMinusColon extends Token
{
    public TTMinusColon(int line, int pos, SourceFile sf)
    {
        super ("-:", line, pos, sf);
    }

    @Override
    public Object clone()
    {
      return new TTMinusColon(getLine(), getPos(), getSource());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTTMinusColon(this);
    }

    @Override
    public void setText(@SuppressWarnings("unused") String text)
    {
        throw new RuntimeException("Cannot change TTMinusColon text.");
    }
}