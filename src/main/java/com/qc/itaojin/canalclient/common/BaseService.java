package com.qc.itaojin.canalclient.common;

import com.qc.itaojin.util.StringUtils;

import java.io.UnsupportedEncodingException;

/**
 * Created by fuqinqin on 2018/7/2.
 */
public class BaseService {

    private String charset = "UTF-8";

    protected byte[] toBytes(String content){
        if(StringUtils.isBlank(content)){
            return null;
        }
        try {
            return content.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;
    }

}
