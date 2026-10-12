package com.scaffold.bookkeeping;

/**
 * 记账业务模块标记类。
 *
 * <p>用于让 Maven 模块在尚未加入业务代码时仍然有明确的包入口，
 * 后续 Controller、Service 与 Mapper 均放在此模块下。</p>
 */
public final class BookkeepingModuleMarker {

    private BookkeepingModuleMarker() {
    }
}
