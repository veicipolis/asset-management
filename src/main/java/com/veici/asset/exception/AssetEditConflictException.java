package com.veici.asset.exception;

public class AssetEditConflictException extends RuntimeException {

    public AssetEditConflictException() {
        super("资产已被其他操作修改，请重新打开编辑页面后再试");
    }
}