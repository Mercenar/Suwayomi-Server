/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.HasGetOp
import suwayomi.tachidesk.graphql.queries.filter.IntFilter
import suwayomi.tachidesk.graphql.queries.filter.LongFilter
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompare
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareEntity
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareString
import suwayomi.tachidesk.graphql.queries.filter.applyOps
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.OrderBy
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.maybeSwap
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 *
 * TODO Mutations
 * - Favorite
 * - Unfavorite
 * - Add to category
 * - Remove from category
 * - Check for updates
 * - Download x(all = -1) chapters
 * - Delete read/all downloaded chapters
 * - Add/update meta
 * - Delete meta
 */
class MangaQuery {
    fun manga(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<MangaType?> {
        return dataFetchingEnvironment.getValueFromDataLoader("MangaDataLoader", id)
    }

    enum class MangaOrderBy(override val column: Column<out Comparable<*>>) : OrderBy<MangaType> {
        ID(MangaTable.id),
        TITLE(MangaTable.title),
        IN_LIBRARY_AT(MangaTable.inLibraryAt),
        LAST_FETCHED_AT(MangaTable.lastFetchedAt);

        override fun greater(cursor: Cursor): Op<Boolean> {
            return when (this) {
                ID -> MangaTable.id greater cursor.value.toInt()
                TITLE -> MangaTable.title greater cursor.value
                IN_LIBRARY_AT -> MangaTable.inLibraryAt greater cursor.value.toLong()
                LAST_FETCHED_AT -> MangaTable.lastFetchedAt greater cursor.value.toLong()
            }
        }

        override fun less(cursor: Cursor): Op<Boolean> {
            return when (this) {
                ID -> MangaTable.id less cursor.value.toInt()
                TITLE -> MangaTable.title less cursor.value
                IN_LIBRARY_AT -> MangaTable.inLibraryAt less cursor.value.toLong()
                LAST_FETCHED_AT -> MangaTable.lastFetchedAt less cursor.value.toLong()
            }
        }

        override fun asCursor(type: MangaType): Cursor {
            val value = when (this) {
                ID -> type.id.toString()
                TITLE -> type.title
                IN_LIBRARY_AT -> type.inLibraryAt.toString()
                LAST_FETCHED_AT -> type.lastFetchedAt.toString()
            }
            return Cursor(value)
        }
    }

    data class MangaCondition(
        val id: Int? = null,
        val sourceId: Long? = null,
        val url: String? = null,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val initialized: Boolean? = null,
        val artist: String? = null,
        val author: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: MangaStatus? = null,
        val inLibrary: Boolean? = null,
        val inLibraryAt: Long? = null,
        val realUrl: String? = null,
        val lastFetchedAt: Long? = null,
        val chaptersLastFetchedAt: Long? = null
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, MangaTable.id)
            opAnd.eq(sourceId, MangaTable.sourceReference)
            opAnd.eq(url, MangaTable.url)
            opAnd.eq(title, MangaTable.title)
            opAnd.eq(thumbnailUrl, MangaTable.thumbnail_url)
            opAnd.eq(initialized, MangaTable.initialized)
            opAnd.eq(artist, MangaTable.artist)
            opAnd.eq(author, MangaTable.author)
            opAnd.eq(description, MangaTable.description)
            opAnd.eq(genre?.joinToString(), MangaTable.genre)
            opAnd.eq(status?.value, MangaTable.status)
            opAnd.eq(inLibrary, MangaTable.inLibrary)
            opAnd.eq(inLibraryAt, MangaTable.inLibraryAt)
            opAnd.eq(realUrl, MangaTable.realUrl)
            opAnd.eq(lastFetchedAt, MangaTable.lastFetchedAt)
            opAnd.eq(chaptersLastFetchedAt, MangaTable.chaptersLastFetchedAt)

            return opAnd.op
        }
    }

    data class MangaFilter(
        val id: IntFilter? = null,
        val sourceId: LongFilter? = null,
        val url: StringFilter? = null,
        val title: StringFilter? = null,
        val thumbnailUrl: StringFilter? = null,
        val initialized: BooleanFilter? = null,
        val artist: StringFilter? = null,
        val author: StringFilter? = null,
        val description: StringFilter? = null,
        // val genre: List<String>? = null, // todo
        // val status: MangaStatus? = null, // todo
        val inLibrary: BooleanFilter? = null,
        val inLibraryAt: LongFilter? = null,
        val realUrl: StringFilter? = null,
        val lastFetchedAt: LongFilter? = null,
        val chaptersLastFetchedAt: LongFilter? = null,
        val category: IntFilter? = null,
        override val and: List<MangaFilter>? = null,
        override val or: List<MangaFilter>? = null,
        override val not: MangaFilter? = null
    ) : Filter<MangaFilter> {
        override fun getOpList(): List<Op<Boolean>> {
            return listOfNotNull(
                andFilterWithCompareEntity(MangaTable.id, id),
                andFilterWithCompare(MangaTable.sourceReference, sourceId),
                andFilterWithCompareString(MangaTable.url, url),
                andFilterWithCompareString(MangaTable.title, title),
                andFilterWithCompareString(MangaTable.thumbnail_url, thumbnailUrl),
                andFilterWithCompare(MangaTable.initialized, initialized),
                andFilterWithCompareString(MangaTable.artist, artist),
                andFilterWithCompareString(MangaTable.author, author),
                andFilterWithCompareString(MangaTable.description, description),
                andFilterWithCompare(MangaTable.inLibrary, inLibrary),
                andFilterWithCompare(MangaTable.inLibraryAt, inLibraryAt),
                andFilterWithCompareString(MangaTable.realUrl, realUrl),
                andFilterWithCompare(MangaTable.inLibraryAt, lastFetchedAt),
                andFilterWithCompare(MangaTable.inLibraryAt, chaptersLastFetchedAt)
            )
        }

        fun getCategoryOp() = andFilterWithCompareEntity(CategoryMangaTable.category, category)
    }

    fun mangas(
        condition: MangaCondition? = null,
        filter: MangaFilter? = null,
        orderBy: MangaOrderBy? = null,
        orderByType: SortOrder? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null
    ): MangaNodeList {
        val queryResults = transaction {
            var res = MangaTable.selectAll()

            val categoryOp = filter?.getCategoryOp()
            if (categoryOp != null) {
                res = MangaTable.innerJoin(CategoryMangaTable)
                    .select { categoryOp }
            }

            res.applyOps(condition, filter)

            if (orderBy != null || (last != null || before != null)) {
                val orderByColumn = orderBy?.column ?: MangaTable.id
                val orderType = orderByType.maybeSwap(last ?: before)

                res.orderBy(orderByColumn, order = orderType)
            }

            val total = res.count()
            val firstResult = res.firstOrNull()?.get(MangaTable.id)?.value
            val lastResult = res.lastOrNull()?.get(MangaTable.id)?.value

            if (after != null) {
                res.andWhere {
                    (orderBy ?: MangaOrderBy.ID).greater(after)
                }
            } else if (before != null) {
                res.andWhere {
                    (orderBy ?: MangaOrderBy.ID).less(before)
                }
            }

            if (first != null) {
                res.limit(first, offset?.toLong() ?: 0)
            } else if (last != null) {
                res.limit(last)
            }

            QueryResults(total, firstResult, lastResult, res.toList())
        }

        val getAsCursor: (MangaType) -> Cursor = (orderBy ?: MangaOrderBy.ID)::asCursor

        val resultsAsType = queryResults.results.map { MangaType(it) }

        return MangaNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        MangaNodeList.MangaEdge(
                            getAsCursor(it),
                            it
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        MangaNodeList.MangaEdge(
                            getAsCursor(it),
                            it
                        )
                    }
                )
            },
            pageInfo = PageInfo(
                hasNextPage = queryResults.lastKey != resultsAsType.lastOrNull()?.id,
                hasPreviousPage = queryResults.firstKey != resultsAsType.firstOrNull()?.id,
                startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(it) },
                endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(it) }
            ),
            totalCount = queryResults.total.toInt()
        )
    }
}
