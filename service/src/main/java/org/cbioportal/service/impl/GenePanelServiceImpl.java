package org.cbioportal.service.impl;

import org.cbioportal.model.GenePanel;
import org.cbioportal.model.GenePanelData;
import org.cbioportal.model.GenePanelToGene;
import org.cbioportal.model.MolecularProfile;
import org.cbioportal.model.Sample;
import org.cbioportal.model.SampleList;
import org.cbioportal.model.meta.BaseMeta;
import org.cbioportal.persistence.GenePanelRepository;
import org.cbioportal.persistence.SampleListRepository;
import org.cbioportal.service.GenePanelService;
import org.cbioportal.service.MolecularProfileService;
import org.cbioportal.service.SampleListService;
import org.cbioportal.service.SampleService;
import org.cbioportal.service.exception.GenePanelNotFoundException;
import org.cbioportal.service.exception.MolecularProfileNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.collections.map.MultiKeyMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GenePanelServiceImpl implements GenePanelService {

    private static final String SEQUENCED_LIST_SUFFIX = "_sequenced";
    
    @Autowired
    private GenePanelRepository genePanelRepository;
    @Autowired
    private MolecularProfileService molecularProfileService;
    @Autowired
    private SampleListRepository sampleListRepository;
    @Autowired
    private SampleListService sampleListService;
    @Autowired
    private SampleService sampleService;

    @Override
    public List<GenePanel> getAllGenePanels(String projection, Integer pageSize, Integer pageNumber, String sortBy, 
                                            String direction) {
        
        List<GenePanel> genePanels = genePanelRepository.getAllGenePanels(projection, pageSize, pageNumber, sortBy, 
            direction);

        if (projection.equals("DETAILED")) {

            List<GenePanelToGene> genePanelToGeneList = genePanelRepository.getGenesOfPanels(genePanels
                .stream().map(GenePanel::getStableId).collect(Collectors.toList()));

            genePanels.forEach(g -> g.setGenes(genePanelToGeneList.stream().filter(p -> p.getGenePanelId()
                .equals(g.getStableId())).collect(Collectors.toList())));
        }
        
        return genePanels;
    }

    @Override
    public BaseMeta getMetaGenePanels() {
        
        return genePanelRepository.getMetaGenePanels();
    }

    @Override
    public GenePanel getGenePanel(String genePanelId) throws GenePanelNotFoundException {

        GenePanel genePanel = genePanelRepository.getGenePanel(genePanelId);
        if (genePanel == null) {
            throw new GenePanelNotFoundException(genePanelId);
        }
        
        genePanel.setGenes(genePanelRepository.getGenesOfPanels(Arrays.asList(genePanelId)));
        return genePanel;
    }

    @Override
	public List<GenePanel> fetchGenePanels(List<String> genePanelIds, String projection) {

        List<GenePanel> genePanels = genePanelRepository.fetchGenePanels(genePanelIds, projection);
        
        if (projection.equals("DETAILED")) {

            List<GenePanelToGene> genePanelToGeneList = genePanelRepository.getGenesOfPanels(genePanels
                .stream().map(GenePanel::getStableId).collect(Collectors.toList()));

            genePanels.forEach(g -> g.setGenes(genePanelToGeneList.stream().filter(p -> p.getGenePanelId()
                .equals(g.getStableId())).collect(Collectors.toList())));
        }

        return genePanels;
	}
    
    @Override
    public List<GenePanelData> getGenePanelData(String molecularProfileId, String sampleListId) 
        throws MolecularProfileNotFoundException {

        molecularProfileService.getMolecularProfile(molecularProfileId);
        List<String> sampleIds = sampleListRepository.getAllSampleIdsInSampleList(sampleListId);
        List<String> molecularProfileIds = new ArrayList<>();
        sampleIds.forEach(s -> molecularProfileIds.add(molecularProfileId));

        List<GenePanelData> genePanelData = genePanelRepository.getGenePanelData(molecularProfileId, sampleListId);
        return createGenePanelData(createGenePanelDataMap(genePanelData), createGenePanelToGeneMap(genePanelData),
                                   molecularProfileIds, sampleIds);
    }

    @Override
    public List<GenePanelData> fetchGenePanelData(String molecularProfileId, List<String> sampleIds) 
        throws MolecularProfileNotFoundException {

        molecularProfileService.getMolecularProfile(molecularProfileId);
        List<String> molecularProfileIds = new ArrayList<>();
        sampleIds.forEach(s -> molecularProfileIds.add(molecularProfileId));

        List<GenePanelData> genePanelData = genePanelRepository.fetchGenePanelData(molecularProfileId, sampleIds);
        return createGenePanelData(createGenePanelDataMap(genePanelData), createGenePanelToGeneMap(genePanelData),
                                   molecularProfileIds, sampleIds);
    }

    @Override
	public List<GenePanelData> fetchGenePanelDataInMultipleMolecularProfiles(List<String> molecularProfileIds,
			List<String> sampleIds) {

            List<GenePanelData> genePanelData =
                genePanelRepository.fetchGenePanelDataInMultipleMolecularProfiles(molecularProfileIds, sampleIds);
            return createGenePanelData(createGenePanelDataMap(genePanelData), createGenePanelToGeneMap(genePanelData),
                                       molecularProfileIds, sampleIds);
	}

    private List<GenePanelData> createGenePanelData(MultiKeyMap genePanelDataMap, MultiKeyMap genePanelToGeneMap, List<String> molecularProfileIds,
        List<String> sampleIds) {

        Map<String, MolecularProfile> molecularProfileMap = molecularProfileService.getMolecularProfiles(
            molecularProfileIds, "SUMMARY").stream().collect(Collectors.toMap(MolecularProfile::getStableId, Function.identity()));
        List<String> studyIds = new ArrayList<>();
        List<String> sequencedSampleListIds = new ArrayList<>();
        List<String> copyMolecularProfileIds = new ArrayList<>(molecularProfileIds);
        
        for (int i = 0; i < copyMolecularProfileIds.size(); i++) {
            String molecularProfileId = copyMolecularProfileIds.get(i);
            if (molecularProfileMap.containsKey(molecularProfileId)) {
                studyIds.add(molecularProfileMap.get(molecularProfileId).getCancerStudyIdentifier());
            } else {
                molecularProfileIds.remove(i);
                sampleIds.remove(i);
            }
        }

        MultiKeyMap samples = createSampleMap(studyIds, sampleIds);
        studyIds.stream().distinct().forEach(s -> sequencedSampleListIds.add(s + SEQUENCED_LIST_SUFFIX));
        Map<String, List<SampleList>> sampleListMap = sampleListService.fetchSampleLists(sequencedSampleListIds, "DETAILED")
            .stream().collect(Collectors.groupingBy(SampleList::getCancerStudyIdentifier));

        List<GenePanelData> resultGenePanelDataList = new ArrayList<>();

        for(int i = 0; i < sampleIds.size(); i++) {
            String sampleId = sampleIds.get(i);
            String molecularProfileId = molecularProfileIds.get(i);
        
            GenePanelData resultGenePanelData = new GenePanelData();
            String studyId = molecularProfileMap.get(molecularProfileId).getCancerStudyIdentifier();
            resultGenePanelData.setStudyId(studyId);
            Optional<GenePanelData> genePanelData =
                Optional.ofNullable((GenePanelData)genePanelDataMap.get(molecularProfileId, sampleId));
            if (genePanelData.isPresent()) {
                String genePanelId = genePanelData.get().getGenePanelId();
                resultGenePanelData.setGenePanelId(genePanelId);
                resultGenePanelData.setWholeExomeSequenced(false);
            } else {
                List<SampleList> sampleLists = sampleListMap.get(studyId);
                if (sampleLists == null || (sampleLists != null && sampleLists.get(0).getSampleIds().contains(sampleId))) {
                    resultGenePanelData.setWholeExomeSequenced(true);
                } else {
                    resultGenePanelData.setWholeExomeSequenced(false);
                }
            }
            resultGenePanelData.setMolecularProfileId(molecularProfileId);
            resultGenePanelData.setSampleId(sampleId);
            Optional<Sample> sample = Optional.ofNullable((Sample)samples.get(sampleId, studyId));
            if (sample.isPresent()) {
                resultGenePanelData.setPatientId(sample.get().getPatientStableId());
                resultGenePanelDataList.add(resultGenePanelData);
            }
        }

        return resultGenePanelDataList;
    }

    private MultiKeyMap createGenePanelDataMap(List<GenePanelData> genePanelDataList)
    {
        MultiKeyMap toReturn = new MultiKeyMap();
        for (GenePanelData gpd : genePanelDataList) {
            toReturn.put(gpd.getMolecularProfileId(), gpd.getSampleId(), gpd);
        }
        return toReturn;
    }

    private MultiKeyMap createGenePanelToGeneMap(List<GenePanelData> genePanelDataList)
    {
        MultiKeyMap toReturn = new MultiKeyMap();
           List<GenePanelToGene> genePanelToGeneList = genePanelRepository.getGenesOfPanels(genePanelDataList.stream()
            .map(GenePanelData::getGenePanelId).collect(Collectors.toList()));
           for (GenePanelToGene gp2g : genePanelToGeneList) {
               toReturn.put(gp2g.getGenePanelId(), gp2g.getEntrezGeneId(), gp2g);
           }
           return toReturn;
    }

    private MultiKeyMap createSampleMap(List<String> studyIds, List<String> sampleIds)
    {
        MultiKeyMap toReturn = new MultiKeyMap();
        for (Sample sample : sampleService.fetchSamples(studyIds, sampleIds, "ID")) {
            toReturn.put(sample.getStableId(), sample.getCancerStudyIdentifier(), sample);
        }
        return toReturn;
    }
}
