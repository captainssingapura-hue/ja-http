package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.Param;

import java.util.Map;

public record EchoHeaders(Map<String, String> headers) implements Param._Header {}
