import json

MAX_ITERS = 5

REVIEWERS = ["code_quality", "performance", "tests", "security", "functional"]

CHECKS = ["format", "lint", "typecheck", "build", "tests"]


def merge_feedback(reviews):
    feedback = []
    for r in reviews:
        if not r.get("approved", False):
            feedback.extend(r.get("blocking", []))
    return feedback


def run_pipeline(agent, plan: str):

    context = json.loads(agent.run("context_builder", plan))
    feedback = []

    for i in range(MAX_ITERS):

        result = agent.run("implementor", {
            "plan": plan,
            "context": context,
            "feedback": feedback,
        })

        impl_output = json.loads(result)

        if impl_output.get("status") == "delegated_to_human":
            return {
                "status": "DELEGATED",
                "reason": impl_output.get("reason", "unspecified ambiguity"),
                "iteration": i + 1,
                "feedback": feedback,
            }

        diff = impl_output.get("diff")

        reviews = []
        for r in REVIEWERS:
            reviews.append(json.loads(agent.run(r, {"diff": diff, "plan": plan})))

        checks = []
        for c in CHECKS:
            checks.append(json.loads(agent.run("check_" + c, {})))

        if all(r.get("approved", False) for r in reviews) and all(checks):

            report = {
                "plan": plan,
                "iterations": i + 1,
                "reviews": reviews,
                "checks": checks,
            }

            agent.run("doc_agent", report)

            return report

        feedback = merge_feedback(reviews)

    return {
        "status": "FAILED",
        "reason": "max_iterations",
        "last_feedback": feedback,
    }
