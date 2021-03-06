/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;
import org.zamia.SourceFile;

@SuppressWarnings("nls")
public final class TKSrecrem extends Token
{
    public TKSrecrem(int line, int pos, SourceFile sf)
    {
        super ("$recrem", line, pos, sf);
    }

    @Override
    public Object clone()
    {
      return new TKSrecrem(getLine(), getPos(), getSource());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTKSrecrem(this);
    }

    @Override
    public void setText(@SuppressWarnings("unused") String text)
    {
        throw new RuntimeException("Cannot change TKSrecrem text.");
    }
}
