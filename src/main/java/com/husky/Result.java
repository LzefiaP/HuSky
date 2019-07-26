package com.husky;

public class Result<T> {
    private String message;//提示信息
    private T result;//返回结果
    private boolean isSuccess;//是否成功

    public Result(T result, boolean isSuccess,String message) {
        this.result = result;
        this.isSuccess = isSuccess;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public static Result fail(){
        return new Result(null,false,"fail");
    }

    public static Result fail(Object result){
        return new Result(result,false,"fail");
    }

    public static Result success(){
        return new Result(null,true,"success");
    }

    public static Result success(Object result){
        return new Result(result,true,"success");
    }


    @Override
    public String toString() {
        return "Result{" +
                "message='" + message + '\'' +
                ", result=" + result +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
