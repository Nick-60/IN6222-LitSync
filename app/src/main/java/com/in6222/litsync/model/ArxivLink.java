package com.in6222.litsync.model;

import com.tickaroo.tikxml.annotation.Attribute;
import com.tickaroo.tikxml.annotation.Xml;

/**
 * arXiv 链接信息。
 */
@Xml(name = "link")
public class ArxivLink {

    @Attribute(name = "href")
    private String href;

    @Attribute(name = "rel")
    private String rel;

    @Attribute(name = "type")
    private String type;

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getRel() {
        return rel;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
