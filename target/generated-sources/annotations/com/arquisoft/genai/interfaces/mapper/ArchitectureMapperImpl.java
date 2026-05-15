package com.arquisoft.genai.interfaces.mapper;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.interfaces.dto.request.ArchitectureGenerationRequest;
import com.arquisoft.genai.interfaces.dto.response.ArchitectureResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-15T22:38:28+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.10 (Microsoft)"
)
@Component
public class ArchitectureMapperImpl implements ArchitectureMapper {

    @Override
    public ArchitectureInput toInput(ArchitectureGenerationRequest request) {
        if ( request == null ) {
            return null;
        }

        ArchitectureInput.ArchitectureInputBuilder architectureInput = ArchitectureInput.builder();

        architectureInput.domain( request.getDomain() );
        List<String> list = request.getQualityAttributes();
        if ( list != null ) {
            architectureInput.qualityAttributes( new ArrayList<String>( list ) );
        }
        List<String> list1 = request.getTechStackConstraints();
        if ( list1 != null ) {
            architectureInput.techStackConstraints( new ArrayList<String>( list1 ) );
        }
        architectureInput.naturalLanguageDescription( request.getNaturalLanguageDescription() );

        return architectureInput.build();
    }

    @Override
    public ArchitectureResponse toResponse(ArchitectureOutput output) {
        if ( output == null ) {
            return null;
        }

        ArchitectureResponse.ArchitectureResponseBuilder architectureResponse = ArchitectureResponse.builder();

        architectureResponse.style( output.getStyle() );
        List<String> list = output.getQualityAttributes();
        if ( list != null ) {
            architectureResponse.qualityAttributes( new ArrayList<String>( list ) );
        }
        Map<String, String> map = output.getDiagrams();
        if ( map != null ) {
            architectureResponse.diagrams( new LinkedHashMap<String, String>( map ) );
        }
        Map<String, String> map1 = output.getDiagramUrls();
        if ( map1 != null ) {
            architectureResponse.diagramUrls( new LinkedHashMap<String, String>( map1 ) );
        }
        architectureResponse.documentation( output.getDocumentation() );
        List<String> list1 = output.getTechStack();
        if ( list1 != null ) {
            architectureResponse.techStack( new ArrayList<String>( list1 ) );
        }
        List<String> list2 = output.getDecisions();
        if ( list2 != null ) {
            architectureResponse.decisions( new ArrayList<String>( list2 ) );
        }
        architectureResponse.generationId( output.getGenerationId() );

        return architectureResponse.build();
    }
}
