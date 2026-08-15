package com.kyant.taglib

data class Metadata(
    val propertyMap: PropertyMap,
    val pictures: Array<Picture> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Metadata

        if (propertyMap != other.propertyMap) return false
        if (!pictures.contentEquals(other.pictures)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = propertyMap.hashCode()
        result = 31 * result + pictures.contentHashCode()
        return result
    }
}