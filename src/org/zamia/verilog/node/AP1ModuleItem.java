/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;

@SuppressWarnings("nls")
public final class AP1ModuleItem extends PModuleItem
{
    private PModuleOrGenerateItemDeclaration _moduleOrGenerateItemDeclaration_;

    public AP1ModuleItem()
    {
        // Constructor
    }

    public AP1ModuleItem(
        @SuppressWarnings("hiding") PModuleOrGenerateItemDeclaration _moduleOrGenerateItemDeclaration_)
    {
        // Constructor
        setModuleOrGenerateItemDeclaration(_moduleOrGenerateItemDeclaration_);

    }

    @Override
    public Object clone()
    {
        return new AP1ModuleItem(
            cloneNode(this._moduleOrGenerateItemDeclaration_));
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAP1ModuleItem(this);
    }

    public PModuleOrGenerateItemDeclaration getModuleOrGenerateItemDeclaration()
    {
        return this._moduleOrGenerateItemDeclaration_;
    }

    public void setModuleOrGenerateItemDeclaration(PModuleOrGenerateItemDeclaration node)
    {
        if(this._moduleOrGenerateItemDeclaration_ != null)
        {
            this._moduleOrGenerateItemDeclaration_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._moduleOrGenerateItemDeclaration_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._moduleOrGenerateItemDeclaration_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._moduleOrGenerateItemDeclaration_ == child)
        {
            this._moduleOrGenerateItemDeclaration_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._moduleOrGenerateItemDeclaration_ == oldChild)
        {
            setModuleOrGenerateItemDeclaration((PModuleOrGenerateItemDeclaration) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}