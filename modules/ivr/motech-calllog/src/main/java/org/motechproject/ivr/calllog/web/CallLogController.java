package org.motechproject.ivr.calllog.web;

import org.codehaus.jackson.map.ObjectMapper;
import org.motechproject.decisiontree.core.CallDetail;
import org.motechproject.decisiontree.server.service.CalllogSearchParameters;
import org.motechproject.decisiontree.server.service.CalllogSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

@Controller
public class CallLogController {    // service methods for angular ui

    @Autowired
    private CalllogSearchService calllogSearchService;

    @RequestMapping("/search")
    @ResponseBody
    public String getAllObjects(@ModelAttribute CalllogSearchParameters params) throws IOException {
        List<CallDetail> result = calllogSearchService.search(params);
        return new ObjectMapper().writeValueAsString(result);
    }
}
