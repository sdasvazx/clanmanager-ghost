package com.clanmanager.clanmanager.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCombatPowerUpdateTest {
    @Test
    void closedSwitchBlocksMemberButNotAdmin() {
        assertThatThrownBy(() -> MemberController.requireCombatPowerEditAllowed(false, true, false, false))
                .isInstanceOf(SecurityException.class)
                .hasMessage("관리자가 공격력 업데이트를 껐습니다.");

        assertThatCode(() -> MemberController.requireCombatPowerEditAllowed(true, false, true, false))
                .doesNotThrowAnyException();
    }

    @Test
    void memberStillCannotEditAnotherMemberOrLockedPower() {
        assertThatThrownBy(() -> MemberController.requireCombatPowerEditAllowed(false, false, false, true))
                .isInstanceOf(SecurityException.class)
                .hasMessage("본인의 전투력만 수정할 수 있습니다.");

        assertThatThrownBy(() -> MemberController.requireCombatPowerEditAllowed(false, true, true, true))
                .isInstanceOf(SecurityException.class)
                .hasMessage("운영진이 전투력 수정을 잠갔습니다.");
    }
}
