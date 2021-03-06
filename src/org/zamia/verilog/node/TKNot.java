/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;
import org.zamia.SourceFile;

@SuppressWarnings("nls")
public final class TKNot extends Token
{
    public TKNot(int line, int pos, SourceFile sf)
    {
        super ("not", line, pos, sf);
    }

    @Override
    public Object clone()
    {
      return new TKNot(getLine(), getPos(), getSource());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTKNot(this);
    }

    @Override
    public void setText(@SuppressWarnings("unused") String text)
    {
        throw new RuntimeException("Cannot change TKNot text.");
    }
}
