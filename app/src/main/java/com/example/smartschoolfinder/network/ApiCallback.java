package com.example.smartschoolfinder.network;

public interface ApiCallback<T> {
    void onSuccess(T data);
    void onError(String message);
}
