/*
 * #%L
 * AIW i2b2 ETL
 * %%
 * Copyright (C) 2012 - 2013 Emory University
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package edu.emory.cci.aiw.i2b2etl.dest.metadata;

/**
 *
 * @author Andrew Post
 */
public class OntologyBuildException extends Exception {

    public OntologyBuildException(Throwable thrwbl) {
        super(thrwbl);
    }

    public OntologyBuildException(String string, Throwable thrwbl) {
        super(string, thrwbl);
    }

    public OntologyBuildException(String string) {
        super(string);
    }

    public OntologyBuildException() {
    }
    
}
