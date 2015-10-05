/*
 * Copyright 2014 The Language Application Grid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package edu.cmu.lti.oaqa.lapps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lappsgrid.api.ProcessingService;
import org.lappsgrid.discriminator.Discriminators;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.DataContainer;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;
import org.lappsgrid.vocabulary.Features;


import static org.lappsgrid.discriminator.Discriminators.Uri;

import org.lappsgrid.metadata.IOSpecification;

import java.util.Map;

public class DBpediaSpotlightAnnotator implements ProcessingService {

    //client that fetchs json annotations from spotlight rest endpoint
    DBpediaSpotlightRestClient spotlightClient;

    ServiceMetadata metadata;

    public DBpediaSpotlightAnnotator() {

        spotlightClient = new DBpediaSpotlightRestClient();

        // Create a metadata object
        metadata = new ServiceMetadata();

        // Populate metadata using setX() methods
        metadata.setName(this.getClass().getName());
        metadata.setDescription("DBpedia Spotlight Named Entity Recognizer");
        metadata.setVersion("1.0.0-SNAPSHOT");
        metadata.setVendor("http://www.lappsgrid.org");
        metadata.setLicense(Uri.APACHE2);

        // JSON for input information
        IOSpecification requires = new IOSpecification();
        requires.addFormats(Uri.TEXT, Uri.LAPPS);
        requires.addLanguage("en");             // Source language

        // JSON for output information
        IOSpecification produces = new IOSpecification();
        produces.addFormat(Uri.LAPPS);          // LIF (form)
        produces.addAnnotation(Uri.NE);         // Named Entity
        requires.addLanguage("en");             // Target language

        // Embed I/O metadata JSON objects
        metadata.setRequires(requires);
        metadata.setProduces(produces);
    }


    @Override
    public String getMetadata() {
        // Create Data instance and populate it
        Data<ServiceMetadata> data = new Data<>(Uri.META, this.metadata);
        return data.asJson();
    }

    @Override
    public String execute(String input) {
        // Step #1: Parse the input.
        Data data = Serializer.parse(input, Data.class);

        // Step #2: Check the discriminator
        final String discriminator = data.getDiscriminator();
        if (discriminator.equals(Discriminators.Uri.ERROR)) {
            // Return the input unchanged.
            return input;
        }

        // Step #3: Extract the text.
        Container container = null;
        if (discriminator.equals(Discriminators.Uri.TEXT)) {
            container = new Container();
            container.setText(data.getPayload().toString());
        } else if (discriminator.equals(Discriminators.Uri.LAPPS)) {
            container = new Container((Map) data.getPayload());
        } else {
            // This is a format we don't accept.
            String message = String.format("Unsupported discriminator type: %s", discriminator);
            return new Data<String>(Discriminators.Uri.ERROR, message).asJson();
        }

        // Step #4: Create a new View
        View view = container.newView();

        // Step #5: Tokenize the text and add annotations.
        String text = container.getText();

        if (text == null || text.isEmpty()) {
            return input;
        }

        JSONObject resultJSON = spotlightClient.fetchJson(text);
        JSONArray entities;
        try {
            entities = resultJSON.getJSONArray("Resources");
        } catch (JSONException e) {
            return new Data<>(Discriminators.Uri.ERROR, "Received invalid response from DBpedia Spotlight API.").asJson();
        }

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            int start = Integer.parseInt(entity.getString("@offset"));
            String surfaceForm = entity.getString("@surfaceForm");
            int end = start + surfaceForm.length();
            Annotation a = view.newAnnotation("spotlight" + (i + 1), Discriminators.Uri.NE, start, end);
            a.addFeature(Features.Token.WORD, surfaceForm);
            a.addFeature("uri", entity.getString("@URI"));
            a.addFeature("support", entity.getString("@support"));
            a.addFeature("types", entity.getString("@types"));
            a.addFeature("similarityScore", entity.getString("@similarityScore"));
            a.addFeature("percentageOfSecondRank", entity.getString("@percentageOfSecondRank"));
            a.addFeature("support", entity.getString("@support"));
        }

        // Step #6: Update the view's metadata. Each view contains metadata about the
        // annotations it contains, in particular the name of the tool that produced the
        // annotations.
        view.addContains(Discriminators.Uri.NE, this.getClass().getName(), "ner:dbpedia-spotlight");

        // Step #7: Create a DataContainer with the result.
        data = new DataContainer(container);

        // Step #8: Serialize the data object and return the JSON.
        return data.asJson();

    }

}