package com.ywzt.datasync.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ywzt.datasync.util.DatasyncUtil;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncConfItem implements Serializable {
    /** 目标表字段名 */
    private String fieldName;
    /** 是否条件字段 */
    private Boolean isCondition;
    /** 是否更新字段 */
    private Boolean isUpdate;
    /** 默认值，支持 ${uuid} 规则 */
    private Object defaultValue;
    /** 来源字段名，支持 ${表.字段} 规则 */
    private String sourceFieldName;
    /** 转换类型 1:字典映射 2/3:日期格式转换 */
    private String columnMapType;
    private List<Map<String, String>> mapDetail;

    public Object getDefaultValue() {
        if (this.defaultValue != null && DatasyncUtil.isRule(this.defaultValue.toString())) {
            String func = this.defaultValue.toString().substring(2, this.defaultValue.toString().lastIndexOf("}"));
            if (func.toLowerCase().startsWith("uuid")) {
                return DatasyncUtil.uuId();
            }
            return null;
        }
        return this.defaultValue;
    }
}
