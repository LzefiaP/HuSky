package com.husky.protocol.zookeeper;

import java.util.List;

public interface ChildListener {
    void childChanged(String path, List<String> children);
}
