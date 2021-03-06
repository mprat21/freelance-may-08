package mbean;
import entities.Freelancer;
import entities.Jobapps;
import entities.Jobs;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.faces.bean.ManagedProperty;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.inject.Inject;
import javax.jms.JMSConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.Queue;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import model.AssignJobModel;
import model.JobsModel;
import svc.JobsSvcImpl;
import utils.SessionUtils;


@Named(value = "assignJobController")
@RequestScoped
public class AssignJobController implements Serializable {

    @Resource(mappedName = "jms/FreelanceDestQueue")
    private Queue freelanceDestQueue;

    @Inject
    @JMSConnectionFactory("java:comp/DefaultJMSConnectionFactory")
    private JMSContext context;
   
    @EJB
    private JobsSvcImpl jobsSvcImpl;
    private List<AssignJobModel> assignJobModelList=new ArrayList<>();
    private List<Jobs> jobList; 
    
    public AssignJobController() {
    }

    public List<AssignJobModel> getAssignJobModelList() {
        return assignJobModelList;
    }

    public void setAssignJobModelList(List<AssignJobModel> assignJobModelList) {
        this.assignJobModelList = assignJobModelList;
    }

    public List<Jobs> getJobList() {
        return jobList;
    }

    public void setJobList(List<Jobs> jobList) {
        this.jobList = jobList;
    }
    
    @PostConstruct
    public void init(){
        HttpServletRequest req = (HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest();
        if(req != null && req.getParameter("jobid") != null){
            Long jobid = Long.parseLong(req.getParameter("jobid"));
            setAssignJobModelList(new ArrayList<>());
            List<Jobapps> jobAppsList = jobsSvcImpl.getFreelancersByJobId(new Jobs(jobid));
            for( Jobapps apps : jobAppsList){
                if( apps.getJobid().getJobid().compareTo(jobid) == 0){
                    getAssignJobModelList().add(new AssignJobModel( String.valueOf(apps.getJobid().getJobid()), apps.getJobid().getTitle(), 
                      apps.getJobid().getDescription(), apps.getJobid().getJobstatus(), String.valueOf(apps.getFid().getUid()), apps.getFid().getUsers().getFirstname()));
                }
            }
        }
    }
    
    public String assignJobToFreelancer(){
        System.out.println("Inside AssignJobController assignJobToFreelancer() :");
        HttpServletRequest req = (HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest();
        if(req.getParameter("jobid") != null && req.getParameter("freeid") != null ){
            String jobid = req.getParameter("jobid"), freelancerIDs = req.getParameter("freeid");
            if( jobsSvcImpl.assignFreelancerToJobId(jobid, freelancerIDs, "Closed") ){
                System.out.println("Updated jobid "+jobid+" by freelance ID "+freelancerIDs+" to Closed");
                HttpSession session = SessionUtils.getSession();
                Long userid= (Long) session.getAttribute("user_id");
                //publish to JMS Queue..
                sendJMSMessageToFreelanceDestQueue("Provider "+userid+" Assigned jobid "+jobid+" to freelance ID "+freelancerIDs+" to Closed");
            }else{
                System.out.println("Failed to update jobid "+jobid+" by freelance ID "+freelancerIDs+" to Closed");
            }
        }
        return "providerJobs?faces-redirect=true";
    }

    private void sendJMSMessageToFreelanceDestQueue(String messageData) {
        context.createProducer().send(freelanceDestQueue, messageData);
    }
}
