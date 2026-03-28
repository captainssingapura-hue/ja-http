package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.Param;

public record PongQueryParam(String message) implements Param._QueryString {}
