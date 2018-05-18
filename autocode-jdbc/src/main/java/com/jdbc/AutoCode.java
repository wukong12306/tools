package com.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
@EnableConfigurationProperties(PropertiesConfig.class)
public class AutoCode {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PropertiesConfig propertiesConfig;


    /*
        固定常量
     */
    final String blank = "    ";  //缩进 4 空格
    final String newline = "\n";  //换行
    final String semicolon = ";"; //分号
    String currentUser;
    SimpleDateFormat sdf;
    Date date;

    /*
        自动代码生成主方法
     */
    public void auto(){

        //确定总循环次数, 和table个数一样
        String tablename = propertiesConfig.getTablename();
        if (tablename == null || "".equals(tablename)){
            log.info("表名输入有误，请输入表名后再次尝试");
            return;
        }


        String[] loop = null;
        if (tablename.contains(",")){
            loop = tablename.split(",");
        }else {
            loop = new String[]{tablename};
        }
        log.info("共需要根据 " + loop.length + " 个表，来生成代码");

        //根据 package 生成 path 路径
        String packageName = propertiesConfig.getPackagename();
        StringBuilder path = new StringBuilder();
        path.append("src" + File.separator + "main" + File.separator + "java" + File.separator);
        if (packageName.contains(".")){
            String[] split = packageName.split("\\.");
            for (int i = 0; i < split.length; i++) {
                path.append(split[i] + File.separator);
            }
        }else {
            path.append(packageName + File.separator);
        }

        //拼接注释 创建时间
        currentUser = System.getProperty("user.name");
        date = new Date();
        sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String createTime = "/**" + newline + " * Created by " + currentUser + " on " + sdf.format(date) + "." + newline + " */";

        //开始总循环
        for (int l = 0; l < loop.length; l++) {
            log.info("开始生成第 " + (l + 1) + " 个表的代码，当前表名: " + loop[l]);
            String tableLowerCase = loop[l].toLowerCase();
            //表name 驼峰
            String tableTuoFeng = changeTuoFeng(tableLowerCase);

            String sqlField = "";
            if (propertiesConfig.isDanbiao()){
                sqlField = "column_name,column_comment,data_type,column_key";
            }else {
                sqlField = "distinct column_name,column_comment,data_type";
            }
            //拼接sql，主要包括 表的属性字段
            String sql = "select " + sqlField + " from information_schema.columns where table_name='" + tableLowerCase +"'";
            List<Entity> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<Entity>(Entity.class));

            //如果是 分库分表，需要再查询出 分库键
            String fenKuKey = "";
            if ( !propertiesConfig.isDanbiao() ){
                sql = "show rule from '" + tableLowerCase + "'";
                Map<String, Object> map = jdbcTemplate.queryForMap(sql);
                Object db_partition_key = map.get("DB_PARTITION_KEY");
                fenKuKey = db_partition_key.toString().toLowerCase();
            }

            //将属性名 修改为驼峰命名
            List<Entity> listTuoFeng = new ArrayList<>();
            String priKey = ""; //当前表主键 字段
            String priKeyTuoFeng = ""; //当前表主键 字段, 驼峰式
            for (int i = 0; i < list.size(); i++) {
                Entity entity = list.get(i);
                entity.setColumnName(entity.getColumnName().toLowerCase());
                Entity clone = (Entity) entity.clone();
                listTuoFeng.add(clone);
                Field[] declaredFields = Entity.class.getDeclaredFields();
                //遍历所有属性
                for (int j = 0; j < declaredFields.length; j++) {
                    try {
                        String name = declaredFields[j].getName();
                        if ( !name.equals("columnName")){
                            continue;
                        }
                        declaredFields[j].setAccessible(true);
                        String field = (String) declaredFields[j].get(entity);
                        //如果属性包含 下划线，按照驼峰方式命名
                        if (field.contains("_")){
                            String[] split = field.split("_");
                            String fieldTuoFeng = "";
                            fieldTuoFeng += split[0];
                            for (int k = 0; k < split.length; k++) {
                                if (k + 1 == split.length)
                                    break;
                                fieldTuoFeng += captureName(split[k+1]);
                            }
                            declaredFields[j].set(clone,fieldTuoFeng);
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                if ("PRI".equals(entity.getColumnKey())) {
                    priKeyTuoFeng = clone.getColumnName();
                    priKey = entity.getColumnName();
                }
            }
            //如果主键为 null, 默认以表名 + _id 为主键
            if ("".equals(priKey)){
                //如果没有设置主键, 默认以表中第一个 包含 _id 的属性为主键
                for (int i = 0; i < list.size(); i++) {
                    Entity entity = list.get(i);
                    if (entity.getColumnName().contains("_id")){
                        priKey = entity.getColumnName();
                        priKeyTuoFeng = changeTuoFeng(priKey);
                        break;
                    }
                }
                //如果属性中没有 包含 _id 的属性，则默认以 表名 + _id 为主键
                if ("".equals(priKey)){
                    priKey = tableLowerCase + "_id";
                    priKeyTuoFeng = tableTuoFeng + "Id";
                }
            }

            //判断是否生成 pojo
            boolean pojo = propertiesConfig.isPojo();
            if (pojo){
                boolean b = autoPojo(packageName, path.toString(), createTime, tableTuoFeng, tableLowerCase, list, listTuoFeng);
                if (!b)
                    return;
            }

            //判断是否生成 dao
            boolean dao = propertiesConfig.isDao();
            if (dao){
                boolean b = autoDao(packageName, path.toString(), createTime, tableTuoFeng, tableLowerCase, priKey, fenKuKey, list, listTuoFeng);
                if (!b)
                    return;
            }

            //判断是否生成 daoImpl
            boolean daoImpl = propertiesConfig.isDaoimpl();
            if (daoImpl){
                boolean b = autoDaoImpl(packageName, path.toString(), createTime, tableTuoFeng, tableLowerCase, priKey, fenKuKey, priKeyTuoFeng, list, listTuoFeng);
                if (!b)
                    return;
            }
            log.info("当前表: " + loop[l] + " 代码已经生成");
            //当前结束
        }
        //总循环结束
        log.info("所有代码生成完成，请在 " + path.toString() + " 目录下，查看已经生成的代码");
    }

    /*
        生成 pojo 对象
     */
    public boolean autoPojo(String packageName, String path, String createTime, String tableName, String table, List<Entity> list, List<Entity> listTuoFeng){

        //拼接文件头
        StringBuilder pojoString = new StringBuilder();
        pojoString.append("package " + packageName + ".pojo"+ semicolon + newline + newline);
        pojoString.append(createTime + newline);
        pojoString.append("public class " + captureName(tableName) + "{" + newline);

        //拼接属性
        pojoString.append(newline);
        for (int i = 0; i < listTuoFeng.size(); i++) {
            Entity entity = listTuoFeng.get(i);
            //拼接属性行
            pojoString.append(blank + "private String "+ entity.getColumnName() + semicolon );
            //拼接 注释
            pojoString.append(blank + "//" + entity.getColumnComment() + newline);
        }

        //拼接 get 和 set 方法
        pojoString.append(newline);
        for (int j = 0; j < listTuoFeng.size(); j++) {
            Entity entity = listTuoFeng.get(j);
            //get
            pojoString.append(blank + "public String get"+ captureName(entity.getColumnName()) +"() {" + newline);
            pojoString.append(blank + blank + "return "+ entity.getColumnName() + semicolon + newline);
            pojoString.append(blank + "}" + newline + newline);
            //set
            pojoString.append(blank + "public void set"+ captureName(entity.getColumnName()) +"(String " + entity.getColumnName() +") {" + newline);
            pojoString.append(blank + blank + "this."+ entity.getColumnName() + " = " + entity.getColumnName() + semicolon + newline);
            pojoString.append(blank + "}" + newline + newline);

        }
        pojoString.append("}");

        //创建文件夹 和 文件, 并将 java 代码写入文件
        String directory = path + "pojo" + File.separator;
        String file = directory + captureName(tableName) + ".java";
        boolean success = fileCreate(directory, file, pojoString.toString());
        if (!success){
            return false;
        } else {
            return true;
        }
    }

    /*
        生成 dao 对象
     */
    public boolean autoDao(String packageName, String path, String createTime, String tableName, String table, String priKey, String fenKuKey, List<Entity> list, List<Entity> listTuoFeng){

        //拼接文件头
        StringBuilder daoString = new StringBuilder();
        daoString.append("package " + packageName + ".dao" + semicolon + newline + newline);
        daoString.append("import " + packageName + ".pojo." + captureName(tableName) + semicolon + newline);
        daoString.append("import java.util.List;" + newline);
        daoString.append(newline);
        daoString.append(createTime + newline);
        daoString.append("public interface " + captureName(tableName) + "Dao " + "{" + newline);

        //如果不是单表时，参数中加入分库键,
        boolean danbiao = propertiesConfig.isDanbiao();
        String temp = "";
        if (!danbiao && !priKey.equals(fenKuKey)){
            temp = ", String " + changeTuoFeng(fenKuKey);
        }

        //拼接方法
        daoString.append(newline);
        daoString.append(blank + "/*" + newline + blank + blank + "查询 by 主键" + newline + blank + " */" + newline);
        daoString.append(blank + captureName(tableName) + " selectByPrimaryKey (String id" + temp + ") " + semicolon + newline);
        daoString.append(newline);
        daoString.append(blank + "/*" + newline + blank + blank + "插入 (包含 null 和 空串)" + newline + blank + " */" + newline);
        daoString.append(blank + "int insert(" + captureName(tableName) + " " + tableName + ");" + newline);
        daoString.append(newline);
        daoString.append(blank + "/*" + newline + blank + blank + "更新 by 主键 (不包含 null 和 空串)" + newline + blank + " */" + newline);
        daoString.append(blank + "int updateByPrimaryKeySelective(" + captureName(tableName) + " " + tableName + ");" + newline);
        daoString.append(newline);
        daoString.append(blank + "/*" + newline + blank + blank + "删除 by 主键" + newline + blank + " */" + newline);
        daoString.append(blank + "int deleteByPrimaryKey(String id" + temp + ");" + newline);
        daoString.append(newline);
        daoString.append(blank + "/*" + newline + blank + blank + "查询 分页" + newline + blank + " */" + newline);
        daoString.append(blank + "List<" + captureName(tableName) + "> selectBySelective(" + captureName(tableName) + " " + tableName + ", "
                + "int start, int limit, String orderByField, String desc);" + newline);
        daoString.append(newline);
        daoString.append(blank + "/*" + newline + blank + blank + "查询 数量" + newline + blank + " */" + newline);
        daoString.append(blank + "long countBySelective(" + captureName(tableName) + " " + tableName + ");" + newline);

        //拼接结束
        daoString.append(newline);
        daoString.append("}");

        //创建文件夹 和 文件, 并将 java 代码写入文件
        String directory = path + "dao" + File.separator;
        String file = directory + captureName(tableName) + "Dao.java";
        boolean success = fileCreate(directory, file, daoString.toString());
        if (success){
            return true;
        } else {
            return false;
        }
    }

    /*
        生成 daoImpl 对象
     */
    public boolean autoDaoImpl(String packageName, String path, String createTime, String tableName, String table, String priKey, String fenKuKey, String priKeyTuoFeng, List<Entity> list, List<Entity> listTuoFeng){

        //拼接文件头
        StringBuilder daoImplString = new StringBuilder();
        daoImplString.append("package " + packageName + ".dao.impl"+ semicolon + newline + newline);
        daoImplString.append("import " + packageName + ".pojo." + captureName(tableName) + semicolon + newline);
        daoImplString.append("import " + packageName + ".dao." + captureName(tableName) + "Dao;" + newline);
        daoImplString.append("import lombok.extern.slf4j.Slf4j;" + newline);
        daoImplString.append("import org.springframework.beans.factory.annotation.Autowired;" + newline);
        daoImplString.append("import org.springframework.jdbc.core.BeanPropertyRowMapper;" + newline);
        daoImplString.append("import org.springframework.jdbc.core.JdbcTemplate;" + newline);
        daoImplString.append("import org.springframework.stereotype.Component;" + newline);
        daoImplString.append("import java.util.ArrayList;" + newline);
        daoImplString.append("import java.util.Arrays;" + newline);
        daoImplString.append("import java.util.List;" + newline);
        daoImplString.append("import com.alibaba.fastjson.JSON;" + newline);
        daoImplString.append("import java.util.Map;" + newline);
        daoImplString.append(newline);

        //分库分表情况下，添加额外注释: 需要声明下当前分库键
        boolean danbiao = propertiesConfig.isDanbiao();
        if (!danbiao){
            createTime = "/**" + newline + " * Created by " + currentUser + " on " + sdf.format(date) + "." + newline
                    + " * 当前表为水平分库, 分库键为: " + fenKuKey + newline + " */";
        }
        daoImplString.append(createTime + newline);

        daoImplString.append("@Component" + newline);
        daoImplString.append("@Slf4j" + newline);
        daoImplString.append("public class " + captureName(tableName) + "DaoImpl implements " + captureName(tableName) + "Dao {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + "@Autowired" + newline);
        daoImplString.append(blank + "private JdbcTemplate jdbcTemplate;" + newline);
        daoImplString.append(newline);

		/*
			拼接 selectByPrimaryKey 方法
		 */

		//如果不是单表时，参数中加入分库键,
        String temp = "";
        if (!danbiao && !priKey.equals(fenKuKey)){
            temp = ", String " + changeTuoFeng(fenKuKey);
        }

        daoImplString.append(blank + "@Override" + newline);
        daoImplString.append(blank + "public " + captureName(tableName) + " selectByPrimaryKey(String id" + temp + ") {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
        daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
        daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);

        //拼接表 字段
        StringBuilder tableFields = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Entity entity = list.get(i);
            tableFields.append(entity.getColumnName() );
            if (i != list.size()-1){
                tableFields.append(",");
            }
        }
        //拼接字段，表，where条件，参数
        daoImplString.append(blank + blank + "sql.append(\"select " + tableFields.toString() );
        daoImplString.append(" from " + table + " where 1=1 " + "\");" + newline);
        daoImplString.append(blank + blank + "sql.append(\"and " + priKey + " = ? \");" + newline);
        daoImplString.append(blank + blank + "params.add(id); " + newline);
        if (!danbiao && !priKey.equals(fenKuKey)){
            daoImplString.append(blank + blank + "sql.append(\"and " + fenKuKey + " = ? \");" + newline);
            daoImplString.append(blank + blank + "params.add(" + changeTuoFeng(fenKuKey) + "); " + newline);
        }
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
        daoImplString.append(blank + blank + captureName(tableName) + " " + tableName
                + " = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), new BeanPropertyRowMapper<"
                + captureName(tableName) + ">("+ captureName(tableName) + ".class));" + newline);
        daoImplString.append(blank + blank + "log.info(\"params : \" + Arrays.toString(params.toArray()) );" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "return " + tableName + semicolon + newline);
        daoImplString.append(blank + "}" + newline);
        daoImplString.append(newline);


		/*
			拼接 insert 方法
		 */

        daoImplString.append(blank + "@Override" + newline);
        daoImplString.append(blank + "public int insert(" + captureName(tableName) + " " + tableName + ") {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
        daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
        daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);


        //拼接字段，表，where条件，参数
        daoImplString.append(blank + blank + "sql.append(\"insert into " + table + " (" + tableFields.toString() + ") \");" + newline);
        daoImplString.append(blank + blank + "sql.append(\"values (");
        for (int i = 0; i < list.size(); i++) {
            daoImplString.append("?");
            if (i+1 != list.size()){
                daoImplString.append(",");
            }
        }
        daoImplString.append(")\");" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
        daoImplString.append(blank + blank + "int rowsAffected = jdbcTemplate.update(sql.toString(), " + newline);
        for (int i = 0; i < listTuoFeng.size(); i++) {
            Entity entity = listTuoFeng.get(i);
            daoImplString.append(blank + blank + blank + blank + tableName + ".get" + captureName(entity.getColumnName()) + "()");
            if (i+1 != list.size()){
                daoImplString.append(",");
            }
            daoImplString.append(newline);
        }
        daoImplString.append(blank + blank + ");" + newline);
        daoImplString.append(blank + blank + "log.info(\"params : \" + " + "JSON.toJSONString(" + tableName + "));" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "return rowsAffected;" + newline);
        daoImplString.append(blank + "}" + newline);
        daoImplString.append(newline);


		/*
			拼接 updateByPrimaryKeySelective 方法
		 */

        daoImplString.append(blank + "@Override" + newline);
        daoImplString.append(blank + "public " + "int" + " updateByPrimaryKeySelective(" + captureName(tableName) + " " + tableName + ") {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
        daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
        daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);

		/*//拼接表 字段
		StringBuilder tableFields = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			Entity entity = list.get(i);
			tableFields.append(entity.getColumnName() );
			if (i != list.size()-1){
				tableFields.append(",");
			}
		}*/

        //拼接字段，表，where条件，参数
        daoImplString.append(blank + blank + "sql.append(\"update " + table + " set \");" + newline);
        int j = 0; //判断 set 属性时，前面需要不需要增加 逗号
        for (int i = 0; i < list.size(); i++) {
            Entity entityTuoFeng = listTuoFeng.get(i);
            Entity entity = list.get(i);
            if (entityTuoFeng.getColumnName().equals(priKeyTuoFeng)){
                continue;
            }
            daoImplString.append(blank + blank + "if ( " + tableName + ".get" + captureName(entityTuoFeng.getColumnName() + "()")
                    + " != null && !\"\".equals("+ tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "()" + ") ) {" + newline);
            daoImplString.append(blank + blank + blank + "sql.append(\""+ entity.getColumnName() + " = ?,");
            daoImplString.append("\");" + newline);
            daoImplString.append(blank + blank + blank + "params.add(" + tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "());" + newline);
            daoImplString.append(blank + blank + "}" + newline);
        }
        daoImplString.append(blank + blank + "sql.deleteCharAt(sql.length() - 1);" + newline);
        daoImplString.append(blank + blank + "sql.append(\" where " + priKey + " = ? \");" + newline); //update 条件不能确定哪个是最后一个，只能在where前面添加 空格
        daoImplString.append(blank + blank + "params.add(" + tableName + ".get" + captureName(priKeyTuoFeng) + "());" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
        daoImplString.append(blank + blank + "int rowsAffected = jdbcTemplate.update(sql.toString(), params.toArray());" + newline);
        daoImplString.append(blank + blank + "log.info(\"params : \" + Arrays.toString(params.toArray()) );" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "return rowsAffected;" + newline);
        daoImplString.append(blank + "}" + newline);
        daoImplString.append(newline);


		/*
			拼接 deleteByPrimaryKey 方法
		 */

        daoImplString.append(blank + "@Override" + newline);
        daoImplString.append(blank + "public " + "int" + " deleteByPrimaryKey(String id" + temp + ") {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "if (id == null || \"\".equals(id)) {" + newline);
        daoImplString.append(blank + blank + blank + "return 0;" + newline);
        daoImplString.append(blank + blank + "}" + newline);

        daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
        daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
        daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);

        daoImplString.append(blank + blank + "sql.append(\"delete from " + table + " where " + priKey + " = ? \");" + newline);
        daoImplString.append(blank + blank + "params.add(id);" + newline);
        if (!danbiao && !priKey.equals(fenKuKey)){
            daoImplString.append(blank + blank + "sql.append(\"and " + fenKuKey + " = ? \");" + newline);
            daoImplString.append(blank + blank + "params.add(" + changeTuoFeng(fenKuKey) + "); " + newline);
        }
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
        daoImplString.append(blank + blank + "int rowsAffected = jdbcTemplate.update(sql.toString(), params.toArray());" + newline);
        daoImplString.append(blank + blank + "log.info(\"params : \" + Arrays.toString(params.toArray()) );" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "return rowsAffected;" + newline);
        daoImplString.append(blank + "}" + newline);
        daoImplString.append(newline);


		/*
			拼接 queryList 方法, 用到很少, 暂不生成
		 */

		/*daoImplString.append(blank + "@Override" + newline);
		daoImplString.append(blank + "public " + "List<" + captureName(tableName) + ">" + " queryList(" + captureName(tableName) + " " + tableName + ") {" + newline);
		daoImplString.append(newline);

		daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
		daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
		daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);

		//拼接字段，表，where条件，参数
		daoImplString.append(blank + blank + "sql.append(\"select " + tableFields.toString() );
		daoImplString.append(" from " + table + " where 1=1 " + "\");" + newline);

		//拼接字段，表，where条件，参数
		for (int i = 0; i < list.size(); i++) {
			Entity entityTuoFeng = listTuoFeng.get(i);
			Entity entity = list.get(i);
			daoImplString.append(blank + blank + "if ( " + tableName + ".get" + captureName(entityTuoFeng.getColumnName())
					+ " != null && !\"\".equals("+ tableName + ".get" + captureName(entityTuoFeng.getColumnName())  + ") ) {" + newline);
			daoImplString.append(blank + blank + blank + "sql.append(\"and "+ entity.getColumnName() + " = ?");
			if (i+1 != list.size()){
				daoImplString.append(",");
			} else {
				daoImplString.append(" ");
			}
			daoImplString.append("\");" + newline);
			daoImplString.append(blank + blank + blank + "params.add(" + tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "());" + newline);
			daoImplString.append(blank + blank + "}" + newline);
		}
		daoImplString.append(newline);

		daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
		daoImplString.append(blank + blank + "List<" + captureName(tableName) + "> resultList "
				+ "= jdbcTemplate.query(sql.toString(), params.toArray(), new BeanPropertyRowMapper<"
				+ captureName(tableName) + ">("+ captureName(tableName) + ".class));" + newline);
		daoImplString.append(blank + blank + "log.info(\"params : \" + Arrays.toString(params.toArray()) );" + newline);
		daoImplString.append(newline);

		daoImplString.append(blank + blank + "return resultList;" + newline);
		daoImplString.append(blank + "}" + newline);
		daoImplString.append(newline);*/


		/*
			拼接 selectBySelective 方法
		 */

        daoImplString.append(blank + "@Override" + newline);
        daoImplString.append(blank + "public " + "List<" + captureName(tableName) + ">" + " selectBySelective("+ captureName(tableName) + " " + tableName + ", int start, int limit, String orderByField, String desc) {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
        daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
        daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);

        //拼接字段，表，where条件，参数
        daoImplString.append(blank + blank + "sql.append(\"select " + tableFields.toString() );
        daoImplString.append(" from " + table + " where 1=1 " + "\");" + newline);

        //拼接字段，表，where条件，参数
        for (int i = 0; i < list.size(); i++) {
            Entity entityTuoFeng = listTuoFeng.get(i);
            Entity entity = list.get(i);
            daoImplString.append(blank + blank + "if ( " + tableName + ".get" + captureName(entityTuoFeng.getColumnName() + "()")
                    + " != null && !\"\".equals("+ tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "()" + ") ) {" + newline);
            daoImplString.append(blank + blank + blank + "sql.append(\"and "+ entity.getColumnName() + " = ? ");

            daoImplString.append("\");" + newline);
            daoImplString.append(blank + blank + blank + "params.add(" + tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "());" + newline);
            daoImplString.append(blank + blank + "}" + newline);
        }
        daoImplString.append(blank + blank + "if (orderByField != null && !\"\".equals(orderByField)){" + newline);
        daoImplString.append(blank + blank + blank + "sql.append(\"order by \" + orderByField + \" \");" + newline);
        daoImplString.append(blank + blank + blank + "if (desc != null && \"desc\".equals(desc)){" + newline);
        daoImplString.append(blank + blank + blank + blank + "sql.append(\"desc \");" + newline);
        daoImplString.append(blank + blank + blank + "}" + newline + blank + blank + "}" + newline);
        daoImplString.append(blank + blank + "sql.append(\" limit ?,? \");" + newline);
        daoImplString.append(blank + blank + "params.add(start);" + newline);
        daoImplString.append(blank + blank + "params.add(limit);" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
        daoImplString.append(blank + blank + "List<" + captureName(tableName) + "> resultList "
                + "= jdbcTemplate.query(sql.toString(), params.toArray(), new BeanPropertyRowMapper<"
                + captureName(tableName) + ">("+ captureName(tableName) + ".class));" + newline);
        daoImplString.append(blank + blank + "log.info(\"params : \" + Arrays.toString(params.toArray()) );" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "return resultList;" + newline);
        daoImplString.append(blank + "}" + newline);
        daoImplString.append(newline);


		/*
			拼接 countBySelective 方法
		 */

        daoImplString.append(blank + "@Override" + newline);
        daoImplString.append(blank + "public " + "long" + " countBySelective("+ captureName(tableName) + " " + tableName + ") {" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//拼接sql 和 params" + newline);
        daoImplString.append(blank + blank + "StringBuffer sql = new StringBuffer();" + newline);
        daoImplString.append(blank + blank + "List params = new ArrayList<>();" + newline);

        //拼接字段，表，where条件，参数
        daoImplString.append(blank + blank + "sql.append(\"select count(*) count ");
        daoImplString.append("from " + table + " where 1=1 " + "\");" + newline);

        //拼接字段，表，where条件，参数
        for (int i = 0; i < list.size(); i++) {
            Entity entityTuoFeng = listTuoFeng.get(i);
            Entity entity = list.get(i);
            daoImplString.append(blank + blank + "if ( " + tableName + ".get" + captureName(entityTuoFeng.getColumnName() + "()")
                    + " != null && !\"\".equals("+ tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "()" + ") ) {" + newline);
            daoImplString.append(blank + blank + blank + "sql.append(\"and "+ entity.getColumnName() + " = ? ");

            daoImplString.append("\");" + newline);
            daoImplString.append(blank + blank + blank + "params.add(" + tableName + ".get" + captureName(entityTuoFeng.getColumnName()) + "());" + newline);
            daoImplString.append(blank + blank + "}" + newline);
        }
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "//springjdbc 框架默认只打印sql，params需手动打印" + newline);
        daoImplString.append(blank + blank + "Map<String, Object> map "
                + "= jdbcTemplate.queryForMap(sql.toString(), params.toArray());" + newline);
        daoImplString.append(blank + blank + "long count = ((Long) map.get(\"count\")).longValue();" + newline);
        daoImplString.append(blank + blank + "log.info(\"params : \" + Arrays.toString(params.toArray()) );" + newline);
        daoImplString.append(newline);

        daoImplString.append(blank + blank + "return count;" + newline);
        daoImplString.append(blank + "}" + newline);
        daoImplString.append(newline);


        //拼接结束
        daoImplString.append("}");

        //创建文件夹 和 文件, 并将 java 代码写入文件
        String directory = path + "dao" + File.separator + "impl" + File.separator;
        String file = directory + captureName(tableName) + "DaoImpl.java";
        boolean success = fileCreate(directory, file, daoImplString.toString());
        if (!success){
            return false;
        } else {
            return true;
        }
    }

    /*
        生成文件和文件夹，并将 java 写入到文件
     */
    public boolean fileCreate(String directoryString, String fileString, String java){
        try {
            File directory = new File(directoryString);
            if (!directory.exists()) {
                directory.mkdirs();// 目录不存在的情况下，创建目录。
            }
            File file = new File(fileString);
            file.createNewFile();

            PrintStream ps = new PrintStream(new FileOutputStream(file));
            ps.println(java.toString());
            return true;
        } catch (IOException e) {
            log.info("创建文件失败，请重试，或者联系 songyongchao@datang.com");
            e.printStackTrace();
            return false;
        }
    }

    /*
        首字母大写
     */
    public static String captureName(String name) {
        //     name = name.substring(0, 1).toUpperCase() + name.substring(1);
        //        return  name;
        char[] cs=name.toCharArray();
        cs[0]-=32;
        return String.valueOf(cs);
    }

    /*
        属性中 包含 _ 时，返回驼峰方式命名
     */
    public String changeTuoFeng(String old){
        if (old.contains("_")){
            String[] split = old.split("_");
            String tuoFeng = "";
            tuoFeng += split[0];
            for (int k = 0; k < split.length; k++) {
                if (k + 1 == split.length)
                    break;
                tuoFeng += captureName(split[k+1]);
            }
            return tuoFeng;
        }
        return old;
    }
}
