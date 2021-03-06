/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.microprofile.graphql.client;

import javax.json.JsonObject;
import java.util.List;
import java.util.Map;

public interface GraphQLResponse {

    JsonObject getData();

    List<GraphQLError> getErrors();

    <T> List<T> getList(Class<T> dataType, String rootField);

    <T> T getObject(Class<T> dataType, String rootField);

    boolean hasData();

    boolean hasError();
    
    public static interface GraphQLError {
        
        String getMessage();

        List<Map<String, Integer>> getLocations();
      
        Object[] getPath();

        Map<String, Object> getExtensions();
        
    }
    
}
