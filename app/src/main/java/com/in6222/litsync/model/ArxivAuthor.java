package com.in6222.litsync.model;

import com.tickaroo.tikxml.annotation.PropertyElement;
import com.tickaroo.tikxml.annotation.Xml;

/**
 * arXiv 作者信息。
 */
@Xml(name = "author")
public class ArxivAuthor {

    @PropertyElement(name = "name")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
