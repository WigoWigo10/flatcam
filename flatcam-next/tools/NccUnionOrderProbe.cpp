// Diagnostic only: independently observe this Windows STL's equal-key STR ordering.
#include <algorithm>
#include <cmath>
#include <fstream>
#include <iostream>
#include <vector>
struct Item { int id; double x, y; };
int main(int argc,char** argv) {
    if(argc!=3 || std::ifstream(argv[2]).good()) return 2;
    std::ifstream input(argv[1]);
    std::vector<Item> items;
    Item next;
    while(input >> next.id >> next.x >> next.y) items.push_back(next);
    if(items.empty() || !input.eof()) return 3;
    std::sort(items.begin(),items.end(),[](auto& a,auto& b){return a.x<b.x;});
    auto slices=static_cast<size_t>(std::ceil(std::sqrt(std::ceil(items.size()/10.))));
    auto capacity=static_cast<size_t>(std::ceil(items.size()/static_cast<double>(slices)));
    for(size_t start=0;start<items.size();start+=capacity) {
        auto end=std::min(start+capacity,items.size());
        std::sort(items.begin()+start,items.begin()+end,[](auto& a,auto& b){return a.y<b.y;});
    }
    std::ofstream output(argv[2]);
    for(auto& item:items) output << item.id << '\n';
    return output.good() ? 0 : 4;
}
