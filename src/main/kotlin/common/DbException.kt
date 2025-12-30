package com.koosco.common

import jdk.internal.joptsimple.internal.Messages.message

/**
 * fileName       : DbException
 * author         : koo
 * date           : 2025. 12. 30. 오후 5:54
 * description    :
 */
class DbException(
    val code: String,
    override val message: String,
) : RuntimeException(message)
