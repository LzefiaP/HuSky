package com.husky.pilot;

import com.alibaba.fastjson.JSONObject;

public interface PilotStrategy<T> {
    T implement(JSONObject jsonRequest);
}
