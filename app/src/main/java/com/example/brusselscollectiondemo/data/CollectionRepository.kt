package com.example.brusselscollectiondemo.data

import com.example.brusselscollectiondemo.network.RealBrusselsScraper

class CollectionRepository(
    private val scraper: RealBrusselsScraper = RealBrusselsScraper()
) {
    suspend fun loadByAddress(query: AddressQuery): Result<CollectionSchedule> {
        return scraper.fetch(query)
    }
}
