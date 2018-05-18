package com.jdbc;

public class Entity implements Cloneable{

    private String columnName;
    private String columnComment;
    private String dataType;
    private String columnKey;

    public String getColumnKey() {
        return columnKey;
    }

    public void setColumnKey(String columnKey) {
        this.columnKey = columnKey;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    @Override
    public Object clone() {
        Object object = null;
        try{
            object = (Entity)super.clone();
        }catch(CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return object;
    }
}
