package com.qc.itaojin.canalclient.kafka;

import com.qc.itaojin.canalclient.canal.entity.CanalOperationEntity;
import com.qc.itaojin.canalclient.common.Constants;
import com.qc.itaojin.canalclient.enums.CanalOperationLevelEnum;
import com.qc.itaojin.canalclient.enums.CanalOperationTypeEnum;
import com.qc.itaojin.canalclient.enums.DataSourceTypeEnum;
import com.qc.itaojin.canalclient.mysql.service.IMysqlService;
import com.qc.itaojin.canalclient.util.JsonUtil;
import com.qc.itaojin.canalclient.util.StringUtils;
import com.qc.itaojin.service.IHBaseService;
import com.qc.itaojin.service.IHiveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @desc kafka消费类
 * @author fuqinqin
 * @date 2018-07-03
 */
@Component
@Slf4j
public class KafkaConsumer {

    @Autowired
    private IHiveService hiveService;
    @Autowired
    private IHBaseService hBaseService;
    @Autowired
    private IMysqlService mysqlService;

    /**
     * 物理数据库业务类型
     * */
    private DataSourceTypeEnum ID;
    /**
     * 线程ID
     * */
    private long threadId;

    @KafkaListener(topics = {"hello"})
    public void processMessage(String content) {
        info("consumer message:{}", content);

        CanalOperationEntity operationEntity = JsonUtil.parse(content, CanalOperationEntity.class);

        // DDL/DML
        CanalOperationLevelEnum operationLevelEnum = operationEntity.getOperationLevel();
        // 增删改
        CanalOperationTypeEnum operationTypeEnum = operationEntity.getOperationType();
        // schema
        String schema = operationEntity.getSchema();
        // table
        String table = operationEntity.getTable();
        ID = operationEntity.getID();
        threadId = operationEntity.getThreadId();

        //DDL
        if(CanalOperationLevelEnum.TABLE.equalsTo(operationLevelEnum)){
            // create table
            if(CanalOperationTypeEnum.CREATE.equalsTo(operationTypeEnum)){
                // 生成HiveSQL
                String hiveSQL = mysqlService.generateHiveSQL(ID, schema, table);
                info("新建Hive和HBase关联表，HiveSQL={}", hiveSQL);
                if(StringUtils.isBlank(hiveSQL)){
                    return;
                }
                String hiveSchema = buildSchema(ID, schema);
                if(StringUtils.isBlank(hiveSchema) || !hiveService.init(hiveSchema, table, hiveSQL)){
                    info("初始化Hive仓库失败，buildHiveSchema={}, table={}, hiveSQL={}", hiveSchema, table, hiveSQL);
                    // TODO 此处记录异常，为人工维护提供数据支持
                    return;
                }

                info("初始化Hive仓库成功！buildHiveSchema={}, table={}, hiveSQL={}", hiveSchema, table, hiveSQL);
            }
        }
        // DML
        else{
            // 命名空间
            String nameSpace = buildSchema(ID, schema);
            // HBase行键
            String rowKey = operationEntity.getRowKey();
            // 变化数据集
            Map<String, String> columnsMap = operationEntity.getColumnsMap();
            // 删除
            if(CanalOperationTypeEnum.DELETE.equalsTo(operationTypeEnum)){
                if(!hBaseService.delete(nameSpace, table, rowKey)){
                    info("删除一行数据失败，nameSpace={}, table={}, rowKey={}", nameSpace, table, rowKey);
                    return;
                }
                info("删除一行数据成功！nameSpace={}, table={}, rowKey={}", nameSpace, table, rowKey);
            }
            // 添加/修改
            else{
                if(!hBaseService.update(nameSpace, table, rowKey, columnsMap)){
                    if(CanalOperationTypeEnum.CREATE.equalsTo(operationTypeEnum)){
                        info("新增数据失败，nameSpace={}, table={}, rowKey={}", nameSpace, table, rowKey);
                    }else{
                        info("修改数据失败，nameSpace={}, table={}, rowKey={}", nameSpace, table, rowKey);
                    }
                    return;
                }

                if(CanalOperationTypeEnum.CREATE.equalsTo(operationTypeEnum)){
                    info("新增数据成功！nameSpace={}, table={}, rowKey={}", nameSpace, table, rowKey);
                }else{
                    info("修改数据成功！nameSpace={}, table={}, rowKey={}", nameSpace, table, rowKey);
                }
            }
        }
    }

    /**
     * 构建hive仓库的schema
     * */
    private String buildSchema(DataSourceTypeEnum dataSourceType, String schema){
        if(dataSourceType==null || StringUtils.isBlank(schema)){
            return null;
        }

        StringBuilder hiveSchema = new StringBuilder();
        hiveSchema.append(dataSourceType.name().toLowerCase())
                .append("000")
                .append(schema);

        return hiveSchema.toString();
    }

    /**
     * 多线程间个性输出日志
     * */
    private void info(String content, Object... objects){
        log.info(String.format(Constants.LOG_TEMPLATE, threadId, ID==null?null:ID.name(), content), objects);
    }
    private void error(String content, Object... objects){
        log.error(String.format(Constants.LOG_TEMPLATE, threadId, ID==null?null:ID.name(), content), objects);
    }
    private void debug(String content, Object... objects){
        log.debug(String.format(Constants.LOG_TEMPLATE, threadId, ID==null?null:ID.name(), content), objects);
    }
    private void warn(String content, Object... objects){
        log.warn(String.format(Constants.LOG_TEMPLATE, threadId, ID==null?null:ID.name(), content), objects);
    }

}
