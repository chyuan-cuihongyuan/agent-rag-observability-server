package cn.chyuan.ai.observability.infrastructure.dao.mapper;

import cn.chyuan.ai.observability.infrastructure.dao.po.AlertSilencePO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 告警静默表 mapper（工单 0179 Y3）：双方言通用语句。
 */
@Mapper
public interface AlertSilenceMapper {

    void insert(AlertSilencePO po);

    List<AlertSilencePO> selectAll();

    int deleteById(long id);
}
