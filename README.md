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

Copyright 2016 Kunal Sheth
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
