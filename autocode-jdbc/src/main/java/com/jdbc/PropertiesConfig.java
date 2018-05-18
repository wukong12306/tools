package com.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auto.code")
// PropertySource默认取application.properties   
//@PropertySource(value = "application.properties")
public class PropertiesConfig {

    public String tablename;
    public String packagename = "com";
    public boolean pojo = true;
    public boolean dao = true;
    public boolean daoimpl = true;
    public boolean danbiao = true;

    public boolean isDanbiao() {
        return danbiao;
    }

    public void setDanbiao(boolean danbiao) {
        this.danbiao = danbiao;
    }

    public boolean isDao() {
        return dao;
    }

    public void setDao(boolean dao) {
        this.dao = dao;
    }

    public boolean isDaoimpl() {
        return daoimpl;
    }

    public void setDaoimpl(boolean daoimpl) {
        this.daoimpl = daoimpl;
    }

    public boolean isPojo() {
        return pojo;
    }

    public void setPojo(boolean pojo) {
        this.pojo = pojo;
    }

    public String getTablename() {
        return tablename;
    }

    public void setTablename(String tablename) {
        this.tablename = tablename;
    }

    public String getPackagename() {
        return packagename;
    }

    public void setPackagename(String packagename) {
        this.packagename = packagename;
    }
}