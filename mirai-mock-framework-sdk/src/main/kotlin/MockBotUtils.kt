@file:Suppress("unused", "RedundantVisibilityModifier")

package com.kasukusakura.miraimockframework

import net.mamoe.mirai.mock.MockBot
import net.mamoe.mirai.mock.contact.MockFriend
import net.mamoe.mirai.mock.contact.MockGroup
import net.mamoe.mirai.mock.contact.MockNormalMember


public fun MockBot.maybeCreateGroup(
    id: Long, name: String,
    initBlock: MockGroup.() -> Unit = {}
): MockGroup {
    getGroup(id)?.let { return it }

    return addGroup(id, name).also(initBlock)
}

public fun MockBot.maybeCreateFriend(
    id: Long, name: String,
    initBlock: MockFriend.() -> Unit = {}
): MockFriend {
    getFriend(id)?.let { return it }

    return addFriend(id, name).also(initBlock)
}

public fun MockGroup.maybeCreateMember(
    id: Long,
    name: String,
    initBlock: MockNormalMember.() -> Unit = {}
): MockNormalMember {
    get(id)?.let { return it }

    return addMember(id, name).also(initBlock)
}
