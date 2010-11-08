/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;

@SuppressWarnings("nls")
public final class AP6BinaryModulePathOperator extends PBinaryModulePathOperator
{
    private TTXor _tXor_;

    public AP6BinaryModulePathOperator()
    {
        // Constructor
    }

    public AP6BinaryModulePathOperator(
        @SuppressWarnings("hiding") TTXor _tXor_)
    {
        // Constructor
        setTXor(_tXor_);

    }

    @Override
    public Object clone()
    {
        return new AP6BinaryModulePathOperator(
            cloneNode(this._tXor_));
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAP6BinaryModulePathOperator(this);
    }

    public TTXor getTXor()
    {
        return this._tXor_;
    }

    public void setTXor(TTXor node)
    {
        if(this._tXor_ != null)
        {
            this._tXor_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._tXor_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._tXor_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._tXor_ == child)
        {
            this._tXor_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._tXor_ == oldChild)
        {
            setTXor((TTXor) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}