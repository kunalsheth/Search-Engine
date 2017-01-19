# Search-Engine
## A rudimentary mock-up of Google

### Getting Started:  
  1. Download Apache Solr  
  2. Copy the “config” directory into the “solr” directory  
  3. Inside the solr directory, run:  
 `bin/solr start`  
 `bin/solr create -c www`  
  4. This code depends on:  
 `commons-lang3`  
 `solr-solrj`  
 `jsoup`  
 `mapdb`  
 `slf4j-api`  
  5. Make sure you have `curl` and `shuf` installed (this code was developed for ubuntu)  
  6. Run the java program, make sure to allocate it about 1G of RAM  
  7. Go to http://localhost:8983 to see the results   

### Please Note:  
#### The Crawler does not acknowledge robots.txt, please be respectful and use are your own discretion 
