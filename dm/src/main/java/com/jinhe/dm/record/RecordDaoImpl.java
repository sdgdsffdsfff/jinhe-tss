package com.jinhe.dm.record;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.jinhe.tss.framework.persistence.TreeSupportDao;

@Repository("RecordDao")
public class RecordDaoImpl extends TreeSupportDao<Record> implements RecordDao {

	public RecordDaoImpl() {
        super(Record.class);
    }
 
	public Record deleteRecord(Record record) {
		Long id = record.getId();
        List<Record> list = getChildrenById(id);
        for(Record entity : list) {
            delete(entity);
        }
        return record;
	}
}